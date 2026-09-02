package com.example.ordersystem.exception;

import com.example.ordersystem.enums.OrderStatus;
import org.springframework.http.HttpStatus;

public class OrderCannotBeUpdatedException extends BusinessException {
    public OrderCannotBeUpdatedException(Long orderId, OrderStatus status) {
        super(
                String.format("Order with ID %d cannot be updated because its status is %s. Only PENDING orders can be updated.", orderId, status),
                HttpStatus.BAD_REQUEST,
                "ORDER_CANNOT_BE_UPDATED"
        );
    }
}
