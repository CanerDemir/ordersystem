package com.example.ordersystem.service.interfaces;

import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.request.ShipmentRequest;
import com.example.ordersystem.dto.response.ShipmentResponse;

public interface ShipmentService {
    ShipmentResponse createShipment(Long orderId, CurrentUser currentUser);
    ShipmentResponse getShipmentByOrderId(Long orderId);
    void shipShipment(Long shipmentId, String trackingNumber, String carrier);
    void moveShipmentToInTransit(Long shipmentId);
    void deliverShipment(Long shipmentId);
    void cancelShipment(Long shipmentId);
}
