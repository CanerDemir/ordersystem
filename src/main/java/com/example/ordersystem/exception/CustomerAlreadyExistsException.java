package com.example.ordersystem.exception;

import org.springframework.http.HttpStatus;

public class CustomerAlreadyExistsException extends BusinessException {
    public CustomerAlreadyExistsException(String email) {
        super(
                "Customer with email " + email + " already exists",
                HttpStatus.CONFLICT,
                "CUSTOMER_ALREADY_EXISTS"
        );
    }
}
