package com.example.ordersystem.repository;

import com.example.ordersystem.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    Optional<Shipment> findByOrderId(Long orderId);
    boolean existsByOrderId(Long orderId);
}
