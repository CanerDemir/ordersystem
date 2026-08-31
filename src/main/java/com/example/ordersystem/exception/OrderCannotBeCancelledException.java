package com.example.ordersystem.exception;

import org.springframework.http.HttpStatus;

public class OrderCannotBeCancelledException extends BusinessException {
    public OrderCannotBeCancelledException(Long orderId) {
        super(
                "Order with " + orderId + "cannot be cancelled!",
                HttpStatus.BAD_REQUEST,
                "ORDER_CONNOT_BE_CANCELLED"
        );
    }
}
