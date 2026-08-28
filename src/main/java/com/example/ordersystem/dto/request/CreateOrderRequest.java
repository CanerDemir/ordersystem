package com.example.ordersystem.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateOrderRequest(

        @NotEmpty(message = "Some products must be added to the cart!")
        @Valid
        List<OrderItemRequest> items,

        @NotNull(message = "Shipping address cannot be null!")
        @Valid
        AddressRequest shippingAddress,

        @Valid
        @NotNull(message = "Billing address cannot be null!")
        AddressRequest billingAddress
) {
}
