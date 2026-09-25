package com.example.ordersystem.exception;

import com.example.ordersystem.enums.ShipmentStatus;
import org.springframework.http.HttpStatus;

public class IllegalShipmentStatusTransitionException extends BusinessException {
    public IllegalShipmentStatusTransitionException(ShipmentStatus currentStatus, ShipmentStatus targetStatus) {
        super(
                String.format("Cannot transition shipment status from %s to %s", currentStatus, targetStatus),
                HttpStatus.CONFLICT,
                "ILLEGAL_SHIPMENT_STATUS_TRANSITION"
        );
    }
}
