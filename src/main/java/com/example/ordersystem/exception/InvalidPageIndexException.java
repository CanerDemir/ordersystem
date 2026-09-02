package com.example.ordersystem.exception;

import org.springframework.http.HttpStatus;

public class InvalidPageIndexException extends BusinessException {
    public InvalidPageIndexException(int requestedPage) {
        super(
                String.format("Page index must not be less than zero. Requested page: %d", requestedPage),
                HttpStatus.BAD_REQUEST,
                "INVALID_PAGE_INDEX"
        );
    }
}
