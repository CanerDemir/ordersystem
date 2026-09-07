package com.example.ordersystem.dto.request;

import com.example.ordersystem.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
        @NotNull(message = "Payment Method id required.")
        PaymentMethod paymentMethod,

        @NotBlank(message = "Idempotency key is required.")
        String idempotencyKey
) {
}
