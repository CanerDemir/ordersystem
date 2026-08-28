package com.example.ordersystem.dto.response;

import com.example.ordersystem.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Instant createdAt,
        OrderStatus status,
        Long customerId,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        AddressResponse shippingAddress,
        AddressResponse billingAddress
) {
}
