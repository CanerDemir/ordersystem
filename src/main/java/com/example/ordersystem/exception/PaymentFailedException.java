package com.example.ordersystem.exception;

import org.springframework.http.HttpStatus;

public class PaymentFailedException extends BusinessException {
    public PaymentFailedException(String reason) {
        super(
                String.format("Payment processing failed: %s", reason),
                HttpStatus.UNPROCESSABLE_ENTITY,
                "PAYMENT_PROCESSING_FAILED"
        );
    }
}
