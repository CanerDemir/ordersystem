package com.example.ordersystem.dto.response;

import com.example.ordersystem.enums.ShipmentStatus;

import java.time.Instant;

public record ShipmentResponse(
        Long id,
        String trackingNumber,
        String carrier,
        ShipmentStatus status,
        Instant shippedAt,
        Instant deliveredAt,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
