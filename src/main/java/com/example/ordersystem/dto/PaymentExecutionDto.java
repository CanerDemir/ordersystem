package com.example.ordersystem.dto;

import com.example.ordersystem.enums.PaymentMethod;

import java.math.BigDecimal;

public record PaymentExecutionDto(
        Long orderId,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String idempotencyKey
) {
}
