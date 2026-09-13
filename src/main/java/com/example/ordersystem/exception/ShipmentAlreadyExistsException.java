package com.example.ordersystem.exception;

import org.springframework.http.HttpStatus;

public class ShipmentAlreadyExistsException extends BusinessException {
    public ShipmentAlreadyExistsException(Long orderId) {
        super(
                String.format("Order with id %d already has a shipment", orderId),
                HttpStatus.CONFLICT,
                "SHIPMENT_ALREADY_EXISTS"
        );
    }
}
