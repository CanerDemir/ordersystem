package com.example.ordersystem.mapper;

import com.example.ordersystem.dto.response.AddressResponse;
import com.example.ordersystem.dto.response.OrderItemResponse;
import com.example.ordersystem.dto.response.OrderResponse;
import com.example.ordersystem.entity.Address;
import com.example.ordersystem.entity.Order;
import com.example.ordersystem.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class OrderMapper {
    public OrderResponse toOrderResponse(Order order) {
        if (order == null) return null;
        return new OrderResponse(
                order.getId(),
                order.getCreatedAt(),
                order.getStatus(),
                order.getCustomer().getId(),
                order.getTotalAmount(),
                toOrderItemResponseList(order.getItems()),
                toAddressResponse(order.getShippingAddress()),
                toAddressResponse(order.getBillingAddress())
        );
    }

    public OrderItemResponse toOrderItemResponse(OrderItem orderItem) {
        if (orderItem == null) return null;
        return new OrderItemResponse(
                orderItem.getId(),
                orderItem.getProductId(),
                orderItem.getProductName(),
                orderItem.getQuantity(),
                orderItem.getUnitPrice(),
                orderItem.getDiscountAmount(),
                orderItem.getLineTotal()
        );
    }

    public List<OrderItemResponse> toOrderItemResponseList(List<OrderItem> orderItems) {
        if (orderItems == null) return Collections.emptyList();
        return orderItems.stream().map(this::toOrderItemResponse).toList();
    }

    public AddressResponse toAddressResponse(Address address) {
        if (address == null) return null;
        return new AddressResponse(
                address.getTitle(),
                address.getCity(),
                address.getDistrict(),
                address.getZipCode(),
                address.getCountry(),
                address.getAddressLine(),
                address.getAddressDetail()
        );
    }
}
