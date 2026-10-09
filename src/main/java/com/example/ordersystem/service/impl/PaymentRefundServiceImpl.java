package com.example.ordersystem.service.impl;

import com.example.ordersystem.dto.RefundExecutionDto;
import com.example.ordersystem.dto.RefundResultDto;
import com.example.ordersystem.dto.request.RefundRequest;
import com.example.ordersystem.dto.response.RefundResponse;
import com.example.ordersystem.entity.Order;
import com.example.ordersystem.entity.Payment;
import com.example.ordersystem.entity.PaymentRefund;
import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.enums.PaymentStatus;
import com.example.ordersystem.enums.RefundStatus;
import com.example.ordersystem.exception.OrderStatusTransitionException;
import com.example.ordersystem.exception.PaymentRefundException;
import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.gateway.PaymentGateway;
import com.example.ordersystem.mapper.RefundMapper;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.PaymentRefundRepository;
import com.example.ordersystem.repository.PaymentRepository;
import com.example.ordersystem.service.interfaces.PaymentRefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentRefundServiceImpl implements PaymentRefundService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final PaymentGateway paymentGateway;
    private final RefundMapper refundMapper;

    @Override
    @Transactional
    public RefundResponse processRefund(Long orderId, Long customerId, RefundRequest request) {
        String idempotencyKey = request.idempotencyKey();

        Optional<PaymentRefund> existingRefund = paymentRefundRepository.findByIdempotencyKey(idempotencyKey);
        if (existingRefund.isPresent()) {
            return refundMapper.toRefundResponse(existingRefund.get());
        }

        Order order = orderRepository.findByIdAndCustomerIdWithLock(orderId, customerId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        Optional<PaymentRefund> lockedRefund = paymentRefundRepository.findByIdempotencyKey(idempotencyKey);
        if (lockedRefund.isPresent()) {
            return  refundMapper.toRefundResponse(lockedRefund.get());
        }

        if (paymentRefundRepository.existsByOrderIdAndStatus(orderId, RefundStatus.SUCCESS)) {
            throw new PaymentRefundException(orderId);
        }

        if (!order.canBeRefunded()) {
            throw new OrderStatusTransitionException(orderId, order.getStatus(), OrderStatus.REFUNDED);
        }

        Payment payment = paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS).orElseThrow(() -> new IllegalStateException("No successful payment found for order: " + orderId));

        PaymentRefund refund = new PaymentRefund(
                payment.getId(),
                order.getId(),
                payment.getAmount(),
                idempotencyKey
        );

        refund = paymentRefundRepository.saveAndFlush(refund);

        RefundExecutionDto executionDto = new RefundExecutionDto(
                payment.getId(),
                payment.getAmount(),
                payment.getTransactionReference(),
                idempotencyKey
        );

        RefundResultDto result = paymentGateway.refundPayment(executionDto);

        if (result.successful()) {
            refund.markAsSuccess(result.refundReference());
            order.refund();
        } else {
            refund.markAsFailed(result.errorMessage());
        }

        return refundMapper.toRefundResponse(refund);
    }
}
