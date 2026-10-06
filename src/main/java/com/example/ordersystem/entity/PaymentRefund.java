package com.example.ordersystem.entity;

import com.example.ordersystem.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "payment_refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentRefund {

    @Id
    @SequenceGenerator(name = "payment_refund_seq", allocationSize = 50, sequenceName = "payment_refund_seq")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payment_refund_seq")
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long paymentId;

    @Column(nullable = false, updatable = false)
    private Long orderId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundStatus status;

    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(length = 100)
    private String providerRefundReference;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant completedAt;

    @Column(length = 500)
    private String failureReason;

    @Version
    private Long version;

    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
    }

    public PaymentRefund(Long paymentId, Long orderId, BigDecimal amount, String idempotencyKey) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        this.paymentId = Objects.requireNonNull(paymentId, "paymentId cannot be null");
        this.orderId = Objects.requireNonNull(orderId, "orderId cannot be null");
        this.amount = amount;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
        this.status = RefundStatus.PENDING;
    }

    // State transition domain helper metotları
    public void markAsSuccess(String providerRefundReference) {
        if (this.status != RefundStatus.PENDING) {
            throw  new IllegalStateException("Refund status is not PENDING");
        }

        if (providerRefundReference == null || providerRefundReference.isBlank()) {
            throw  new IllegalArgumentException("providerRefundReference cannot be null or blank");
        }

        this.status = RefundStatus.SUCCESS;
        this.providerRefundReference = providerRefundReference;
        this.completedAt = Instant.now();
        this.failureReason = null;
    }

    public void markAsFailed(String failureReason) {
        if (this.status != RefundStatus.PENDING) {
            throw  new IllegalStateException("Refund status is not PENDING");
        }

        this.status = RefundStatus.FAILED;
        this.failureReason = failureReason;
        this.completedAt = Instant.now();
    }
}
