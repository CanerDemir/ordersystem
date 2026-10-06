package com.example.ordersystem.exception;

import org.springframework.http.HttpStatus;

public class PaymentRefundException extends BusinessException {
    public PaymentRefundException(Long orderId) {
        super(
                String.format("Order has already been successfully refunded: %s", orderId),
                HttpStatus.CONFLICT,
                "ORDER_ALREADY_REFUNDED"
        );
    }
}
