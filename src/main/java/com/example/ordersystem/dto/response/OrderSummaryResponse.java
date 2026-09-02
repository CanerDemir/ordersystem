package com.example.ordersystem.dto.response;

import com.example.ordersystem.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryResponse(
        Long id,
        Instant createdAt,
        OrderStatus status,
        BigDecimal totalAmount,
        Integer totalQuantity
) {
}
