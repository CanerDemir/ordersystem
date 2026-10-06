package com.example.ordersystem.repository;

import com.example.ordersystem.entity.PaymentRefund;
import com.example.ordersystem.enums.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {

    Optional<PaymentRefund> findByIdempotencyKey(String idempotencyKey);

    boolean existsByOrderIdAndStatus(Long orderId, RefundStatus status);
}
