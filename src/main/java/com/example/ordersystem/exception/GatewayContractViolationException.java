package com.example.ordersystem.exception;

import org.springframework.http.HttpStatus;

public class GatewayContractViolationException extends BusinessException {
    public GatewayContractViolationException(String message) {
        super(
                message,
                HttpStatus.BAD_REQUEST,
                "GATEWAY_CONTRACT_VIOLATION"
        );
    }
}
