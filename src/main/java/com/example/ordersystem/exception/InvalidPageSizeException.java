package com.example.ordersystem.exception;

import org.springframework.http.HttpStatus;

public class InvalidPageSizeException extends BusinessException {
    public InvalidPageSizeException(int requestedSize, int maxSize) {
        super(
                String.format("Page size %d exceeds maximum allowed limit of %d", requestedSize, maxSize),
                HttpStatus.BAD_REQUEST,
                "INVALID_PAGE_SIZE"
        );
    }
}
