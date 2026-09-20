package com.example.ordersystem.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentSucceededEvent(
        UUID eventId,
        Long paymentId,
        Long orderId,
        Long customerId,
        BigDecimal amount,
        Instant paidAt
) {
    public PaymentSucceededEvent {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(paymentId, "paymentId cannot be null");
        Objects.requireNonNull(orderId, "orderId cannot be null");
        Objects.requireNonNull(customerId, "customerId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(paidAt, "paidAt cannot be null");

        if (paymentId <= 0) throw new IllegalArgumentException("paymentId must be positive");
        if (orderId <= 0) throw new IllegalArgumentException("orderId must be positive");
        if (customerId <= 0) throw new IllegalArgumentException("customerId must be positive");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("amount must be positive");
    }
}