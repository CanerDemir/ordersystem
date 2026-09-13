package com.example.ordersystem.entity;

import com.example.ordersystem.enums.ShipmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "shipments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment {

    @Id
    @SequenceGenerator(
            name = "shipment_seq",
            sequenceName = "shipment_seq",
            allocationSize = 50
    )
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "shipment_seq")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orderId", nullable = false, unique = true)
    private Order order;

    @Column
    private String trackingNumber;

    @Column
    private String carrier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status;

    @Column
    private Instant shippedAt;

    @Column
    private Instant deliveredAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public static Shipment createReady(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Cannot create ready for null order");
        }
        Shipment shipment = new Shipment();
        shipment.order = order;
        shipment.status = ShipmentStatus.READY;
        return shipment;
    }

    public void markAsShipped(String trackingNumber,  String carrier) {
        if (this.status != ShipmentStatus.READY) {
            throw new IllegalStateException("Only READY shipments can be transitioned to SHIPPED status.");
        }
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("Tracking number is required for SHIPPED status.");
        }
        if (carrier == null || carrier.isBlank()) {
            throw new IllegalArgumentException("Carrier is required for SHIPPED status.");
        }

        this.trackingNumber = trackingNumber;
        this.carrier = carrier;
        this.status = ShipmentStatus.SHIPPED;
        this.shippedAt = Instant.now();
    }

    public void markAsInTransit() {
        if (this.status != ShipmentStatus.SHIPPED) {
            throw new IllegalStateException("Only SHIPPED shipments can be transitioned to IN_TRANSIT status.");
        }
        this.status = ShipmentStatus.IN_TRANSIT;
    }

    public void markAsDelivered() {
        if (this.status != ShipmentStatus.IN_TRANSIT) {
            throw new IllegalStateException("Only IN_TRANSIT shipments can be transitioned to DELIVERED status.");
        }
        this.status = ShipmentStatus.DELIVERED;
        this.deliveredAt = Instant.now();
    }

    public void cancel() {
        if (this.status != ShipmentStatus.READY) {
            throw new IllegalStateException("Only READY shipments can be cancelled in this version.");
        }
        this.status = ShipmentStatus.CANCELLED;
    }
}
