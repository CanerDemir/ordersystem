package com.example.ordersystem.dto.response;

import com.example.ordersystem.enums.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record RefundResponse(
        Long refundId,
        Long paymentId,
        Long orderId,
        BigDecimal amount,
        RefundStatus status,
        String providerRefundReference,
        String failureReason,
        Instant createdAt,
        Instant completedAt
) {
}
