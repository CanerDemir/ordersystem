package com.example.ordersystem.service.impl;

import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.response.ShipmentResponse;
import com.example.ordersystem.entity.Order;
import com.example.ordersystem.entity.Shipment;
import com.example.ordersystem.exception.OrderNotPaidException;
import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.exception.ShipmentAlreadyExistsException;
import com.example.ordersystem.mapper.ShipmentMapper;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ShipmentRepository;
import com.example.ordersystem.service.interfaces.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShipmentServiceImpl implements ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final ShipmentMapper shipmentMapper;

    @Override
    @Transactional
    public ShipmentResponse createShipment(Long orderId, CurrentUser currentUser) {
        Order order = orderRepository.findByIdAndCustomerIdWithLock(orderId, currentUser.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Order",  orderId));

        if (!order.isPaid()) {
            throw new OrderNotPaidException(orderId);
        }

        if (shipmentRepository.existsByOrderId(orderId)) {
            throw new ShipmentAlreadyExistsException(orderId);
        }

        Shipment shipment = shipmentRepository.save(Shipment.createReady(order));
        return shipmentMapper.toShipmentResponse(shipment);
    }

    @Override
    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentByOrderId(Long orderId) {
        Shipment shipment = shipmentRepository.findByOrderId(orderId).orElseThrow(() -> new ResourceNotFoundException("Shipment",  orderId));
        return shipmentMapper.toShipmentResponse(shipment);
    }

    @Override
    @Transactional
    public void shipShipment(Long shipmentId, String trackingNumber, String carrier) {
        Shipment shipment = shipmentRepository.findById(shipmentId).orElseThrow(() -> new ResourceNotFoundException("Shipment",  shipmentId));
        shipment.markAsShipped(trackingNumber, carrier);
    }

    @Override
    @Transactional
    public void moveShipmentToInTransit(Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId).orElseThrow(() -> new ResourceNotFoundException("Shipment",  shipmentId));
        shipment.markAsInTransit();
    }

    @Override
    @Transactional
    public void deliverShipment(Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId).orElseThrow(() -> new ResourceNotFoundException("Shipment",  shipmentId));
        shipment.markAsDelivered();
    }

    @Override
    @Transactional
    public void cancelShipment(Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId).orElseThrow(() -> new ResourceNotFoundException("Shipment",  shipmentId));
        shipment.cancel();
    }
}
