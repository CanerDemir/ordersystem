package com.example.ordersystem.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddCartItemRequest(
        @NotNull(message = "Product Id is required")
        @Positive
        Long productId,

        @NotNull(message = "Quantity is required")
        @Positive
        Integer quantity
) {
}
