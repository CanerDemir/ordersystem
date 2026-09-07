package com.example.ordersystem.repository;

import com.example.ordersystem.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderIdAndIdempotencyKey(Long orderId, String idempotencyKey);
    Optional<Payment> findByOrderIdAndCustomerIdAndIdempotencyKey(Long orderId, Long customerId, String idempotencyKey);
}
