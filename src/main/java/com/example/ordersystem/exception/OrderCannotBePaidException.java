package com.example.ordersystem.exception;

import com.example.ordersystem.enums.OrderStatus;
import org.springframework.http.HttpStatus;

public class OrderCannotBePaidException extends BusinessException {
    public OrderCannotBePaidException(Long orderId, OrderStatus orderStatus) {
        super(
                String.format("Order with ID %d cannot be paid because its current status is %s. Only PENDING orders can be paid.", orderId, orderStatus),
                HttpStatus.BAD_REQUEST,
                "ORDER_CANNOT_BE_PAID"
        );
    }
}
