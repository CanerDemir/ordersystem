package com.example.ordersystem.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(
        @NotNull(message = "Shipping address cannot be null!")
        @Valid
        AddressRequest shippingAddress,

        @Valid
        @NotNull(message = "Billing address cannot be null!")
        AddressRequest billingAddress
) {
}

