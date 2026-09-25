package com.example.ordersystem.service.interfaces;

import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.response.ShipmentResponse;

public interface ShipmentService {
    ShipmentResponse getShipmentByOrderId(Long orderId);
    ShipmentResponse shipShipment(Long shipmentId, String trackingNumber, String carrier);
    void moveShipmentToInTransit(Long shipmentId);
    void deliverShipment(Long shipmentId);
    void cancelShipment(Long shipmentId);
}
