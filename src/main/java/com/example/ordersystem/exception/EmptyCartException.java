package com.example.ordersystem.exception;

import org.springframework.http.HttpStatus;

public class EmptyCartException extends BusinessException {
    public EmptyCartException() {
        super(
                "The cart is empty so checkout cannot be processed.",
                HttpStatus.BAD_REQUEST,
                "EMPTY_CART"
        );
    }
}
