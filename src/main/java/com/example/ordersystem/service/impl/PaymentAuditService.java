package com.example.ordersystem.service.impl;

import com.example.ordersystem.entity.Payment;
import com.example.ordersystem.enums.PaymentMethod;
import com.example.ordersystem.enums.PaymentStatus;
import com.example.ordersystem.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentAuditService {
    private final PaymentRepository paymentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment recordFailedPayment(Long orderId, Long customerId, BigDecimal amount, PaymentMethod paymentMethod, String idempotencyKey) {
        Payment failedPayment = new Payment(
                orderId,
                customerId,
                amount,
                paymentMethod,
                PaymentStatus.FAILED,
                idempotencyKey,
                null
        );
        return paymentRepository.save(failedPayment);
    }
}
