package com.example.ordersystem.exception;

import org.springframework.http.HttpStatus;

public class CartItemNotFoundException extends BusinessException {
    public CartItemNotFoundException(Long productId) {
        super(
                String.format("Product not found in cart with id: %s", productId),
                HttpStatus.NOT_FOUND,
                "CART_ITEM_NOT_FOUND"
        );
    }
}
