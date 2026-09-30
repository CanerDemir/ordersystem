package com.example.ordersystem.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateCartItemRequest(
        @NotNull(message = "Quantity is required")
        @Positive
        Integer quantity
) {
}
