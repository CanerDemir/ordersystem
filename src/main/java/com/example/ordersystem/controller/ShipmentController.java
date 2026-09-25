package com.example.ordersystem.controller;

import com.example.ordersystem.dto.request.ShipShipmentRequest;
import com.example.ordersystem.dto.response.ShipmentResponse;
import com.example.ordersystem.service.interfaces.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    @GetMapping("/orders/{orderId}/shipment")
    @PreAuthorize("hasRole('CUSTOMER') and @orderSecurity.isOrderOwner(#orderId)")
    public ResponseEntity<ShipmentResponse> getShipmentByOrderId(@PathVariable("orderId") Long orderId)  {
        ShipmentResponse response = shipmentService.getShipmentByOrderId(orderId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/shipments/{shipmentId}/ship")
    @PreAuthorize("hasRole('OPERATION')")
    public ResponseEntity<ShipmentResponse> shipShipment(@PathVariable Long shipmentId, @Valid @RequestBody ShipShipmentRequest  request) {
        ShipmentResponse response = shipmentService.shipShipment(shipmentId, request.trackingNumber(),  request.carrier());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/shipments/{shipmentId}/transit")
    @PreAuthorize("hasRole('OPERATION')")
    public ResponseEntity<Void> moveShipmentToInTransit(@PathVariable Long shipmentId) {
        shipmentService.moveShipmentToInTransit(shipmentId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/shipments/{shipmentId}/deliver")
    @PreAuthorize("hasRole('OPERATION')")
    public ResponseEntity<Void> deliverShipment(@PathVariable Long shipmentId) {
        shipmentService.deliverShipment(shipmentId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/shipments/{shipmentId}/cancel")
    @PreAuthorize("hasRole('OPERATION')")
    public ResponseEntity<Void> cancelShipment(@PathVariable Long shipmentId) {
        shipmentService.cancelShipment(shipmentId);
        return ResponseEntity.noContent().build();
    }
}
