package com.example.ordersystem.dto.response;

import com.example.ordersystem.enums.PaymentMethod;
import com.example.ordersystem.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long paymentId,
        Long orderId,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        String transactionReference,
        Instant processedAt
) {
}