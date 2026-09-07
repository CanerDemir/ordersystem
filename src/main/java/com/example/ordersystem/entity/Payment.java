package com.example.ordersystem.entity;

import com.example.ordersystem.enums.PaymentMethod;
import com.example.ordersystem.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_order_idempotency",
                        columnNames = {"orderId", "idempotencyKey"}
                )
        },
        indexes = {
                @Index(name = "idx_payment_order_idempotency", columnList = "orderId, idempotencyKey"),
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Payment {
    @Id
    @SequenceGenerator(name = "payment_seq", allocationSize = 50, sequenceName = "payment_seq")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payment_seq")
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus  paymentStatus;

    @Column(nullable = false)
    private String idempotencyKey;

    private String transactionReference;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public Payment(Long orderId, Long customerId, BigDecimal amount, PaymentMethod paymentMethod, PaymentStatus status, String idempotencyKey, String transactionReference) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = status;
        this.idempotencyKey = idempotencyKey;
        this.transactionReference = transactionReference;
    }

    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markSuccessful(String transactionReference) {
        if (this.paymentStatus == PaymentStatus.SUCCESS) {
            return; // Already in desired state (Idempotent call)
        }
        if (this.paymentStatus == PaymentStatus.FAILED) {
            throw new IllegalStateException("Cannot mark a FAILED payment as SUCCESS.");
        }
        if (transactionReference == null || transactionReference.isBlank()) {
            throw new IllegalArgumentException("Transaction reference is required for SUCCESSFUL payments.");
        }
        this.paymentStatus = PaymentStatus.SUCCESS;
        this.transactionReference = transactionReference;
    }

    public void markFailed() {
        if (this.paymentStatus == PaymentStatus.FAILED) {
            return; // Already in desired state
        }
        if (this.paymentStatus == PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Cannot mark a SUCCESSFUL payment as FAILED.");
        }
        this.paymentStatus = PaymentStatus.FAILED;
    }
}
