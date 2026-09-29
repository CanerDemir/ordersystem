package com.example.ordersystem.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductUpdateRequest(
        @NotBlank(message = "Name field is required")
        @Size(max = 150)
        String name,

        @NotNull(message = "Price field is required")
        @Positive
        BigDecimal price,

        @NotNull(message = "Stock field is required")
        @PositiveOrZero
        Integer stock,

        @Size(max = 1000)
        String description
) {
}
