package com.example.ordersystem.dto.response;

import com.example.ordersystem.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        BigDecimal price,
        Integer stock,
        String description,
        ProductStatus status,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
