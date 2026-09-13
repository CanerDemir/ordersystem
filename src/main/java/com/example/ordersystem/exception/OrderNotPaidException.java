package com.example.ordersystem.exception;

import org.springframework.http.HttpStatus;

public class OrderNotPaidException extends BusinessException {
    public OrderNotPaidException(Long orderId) {
        super(
                String.format("Order with id %d is not in PAID status",  orderId),
                HttpStatus.BAD_REQUEST,
                "ORDER_NOT_PAID"
        );
    }
}
