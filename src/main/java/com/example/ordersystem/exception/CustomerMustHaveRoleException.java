package com.example.ordersystem.exception;

import org.springframework.http.HttpStatus;

public class CustomerMustHaveRoleException extends BusinessException {
    public CustomerMustHaveRoleException(String message) {
        super(
                message,
                HttpStatus.BAD_REQUEST,
                "AT_LEAST_ONE_ROLE"
        );
    }
}
