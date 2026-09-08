package com.example.ordersystem.service.impl;

import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.PaymentExecutionDto;
import com.example.ordersystem.dto.PaymentResultDto;
import com.example.ordersystem.dto.request.PaymentRequest;
import com.example.ordersystem.dto.response.PaymentResponse;
import com.example.ordersystem.entity.Order;
import com.example.ordersystem.entity.Payment;
import com.example.ordersystem.enums.PaymentStatus;
import com.example.ordersystem.exception.GatewayContractViolationException;
import com.example.ordersystem.exception.PaymentFailedException;
import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.gateway.PaymentGateway;
import com.example.ordersystem.mapper.PaymentMapper;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.PaymentRepository;
import com.example.ordersystem.service.interfaces.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.hibernate.internal.util.StringHelper.isBlank;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentMapper paymentMapper;
    private final PaymentAuditService paymentAuditService;

    @Transactional
    public PaymentResponse processOrderPayment(Long orderId, PaymentRequest request, CurrentUser currentUser) {
        Optional<Payment> fastPathPayment = paymentRepository.findByOrderIdAndCustomerIdAndIdempotencyKey(orderId, currentUser.customerId(), request.idempotencyKey());
        if (fastPathPayment.isPresent()) {
            return handleExistingPayment(fastPathPayment.get());
        }

        Order order = orderRepository.findByIdAndCustomerIdWithLock(orderId, currentUser.customerId()).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        Optional<Payment> lockedPayment = paymentRepository.findByOrderIdAndIdempotencyKey(order.getId(), request.idempotencyKey());
        if (lockedPayment.isPresent()) {
            return handleExistingPayment(lockedPayment.get());
        }

        order.isPayable();

        PaymentExecutionDto executionDto = new PaymentExecutionDto(
                order.getId(),
                order.getTotalAmount(),
                request.paymentMethod(),
                request.idempotencyKey()
        );
        PaymentResultDto result = paymentGateway.processPayment(executionDto);

        if (result.successful() && (result.transactionReference() == null || result.transactionReference().isBlank())) {
            String errorMsg = "Payment provider returned successful=true but transactionReference was null or blank";

            paymentAuditService.recordFailedPayment(
                    order.getId(),
                    currentUser.customerId(),
                    order.getTotalAmount(),
                    request.paymentMethod(),
                    request.idempotencyKey()
            );

            throw new GatewayContractViolationException(errorMsg);
        }

        if (!result.successful()) {
            paymentAuditService.recordFailedPayment(
                    order.getId(),
                    currentUser.customerId(),
                    order.getTotalAmount(),
                    request.paymentMethod(),
                    request.idempotencyKey()
            );

            throw new PaymentFailedException(result.errorMessage());
        }

        order.markAsPaid();

        Payment successPayment = new Payment(
                orderId,
                currentUser.customerId(),
                order.getTotalAmount(),
                request.paymentMethod(),
                PaymentStatus.SUCCESS,
                request.idempotencyKey(),
                result.transactionReference()
        );
        Payment savedPayment = paymentRepository.save(successPayment);
        return paymentMapper.toPaymentResponse(savedPayment);
    }

    private PaymentResponse handleExistingPayment(Payment payment) {
        if (payment.getPaymentStatus() == PaymentStatus.FAILED) {
            throw new PaymentFailedException("Previous payment attempt with this idempotency key failed. Please try again with a new key.");
        }
        return paymentMapper.toPaymentResponse(payment);
    }
}
