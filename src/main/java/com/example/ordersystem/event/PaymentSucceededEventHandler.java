package com.example.ordersystem.event;

import com.example.ordersystem.entity.Order;
import com.example.ordersystem.entity.Shipment;
import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ProcessedEventRepository;
import com.example.ordersystem.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSucceededEventHandler {

    private final ProcessedEventRepository processedEventRepository;
    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;

    @Transactional
    public void handle(PaymentSucceededEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("PaymentSucceededEvent cannot be null");
        }

        String eventId = event.eventId().toString();

        // 1. Atomic INSERT via PostgreSQL ON CONFLICT DO NOTHING
        int insertedRows = processedEventRepository.insertIfNotExists(eventId, Instant.now());

        if (insertedRows == 0) {
            log.info("Duplicate event detected. Ignoring processing. eventId={}, orderId={}", eventId, event.orderId());
            return;
        }

        log.info("Processing PaymentSucceededEvent for the first time. eventId={}, orderId={}", eventId, event.orderId());

        Order order = orderRepository.findById(event.orderId()).orElseThrow(() -> new ResourceNotFoundException("Order", event.orderId()));

        if (shipmentRepository.existsByOrderId(event.orderId())) {
            throw new IllegalStateException("Shipment already exists for orderId: " + event.orderId());
        }

        // 4. Create READY Shipment
        Shipment shipment = Shipment.createReady(order);
        shipmentRepository.save(shipment);

        log.info("Successfully created READY shipment. shipmentId={}, orderId={}, eventId={}",
                shipment.getId(), order.getId(), eventId);
    }
}
