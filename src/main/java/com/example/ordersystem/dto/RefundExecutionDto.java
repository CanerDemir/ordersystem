package com.example.ordersystem.dto;

import java.math.BigDecimal;

public record RefundExecutionDto(
        Long paymentId,
        BigDecimal amount,
        String transactionReference,
        String idempotencyKey
) {
}
