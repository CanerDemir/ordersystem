package com.example.ordersystem.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
        @NotNull(message = "A product must be selected!")
        Long productId,

        @NotNull(message = "The product must be at least 1 quantity!")
        @Positive
        Integer quantity
) {
}
