package com.example.ordersystem.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ShipmentCreateRequest(
        @NotNull(message = "Order ID cannot be null")
        @Positive(message = "Order ID must be positive")
        Long orderId
) {
}
