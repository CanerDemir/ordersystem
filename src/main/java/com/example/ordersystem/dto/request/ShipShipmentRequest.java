package com.example.ordersystem.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ShipShipmentRequest(
        @NotBlank(message = "Tracking Number cannot be blank")
        String trackingNumber,

        @NotBlank(message = "Carrier info cannot be blank")
        String carrier
) {
}
