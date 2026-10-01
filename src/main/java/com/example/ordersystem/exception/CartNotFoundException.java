package com.example.ordersystem.exception;

import org.springframework.http.HttpStatus;

public class CartNotFoundException extends BusinessException {
    public CartNotFoundException(Long customerId) {
        super(
                String.format("Cart not found for customer id: %s", customerId),
                HttpStatus.NOT_FOUND,
                "CART_NOT_FOUND"
        );
    }
}
