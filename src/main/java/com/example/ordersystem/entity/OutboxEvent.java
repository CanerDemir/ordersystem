package com.example.ordersystem.entity;

import com.example.ordersystem.enums.AggregateType;
import com.example.ordersystem.enums.EventType;
import com.example.ordersystem.enums.OutboxEventStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @SequenceGenerator(
            name = "outbox_event_seq",
            sequenceName = "outbox_event_seq",
            allocationSize = 50
    )
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "outbox_event_seq")
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AggregateType aggregateType;

    @Column(nullable = false)
    private Long aggregateId;

    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEventStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant publishedAt;

    @Column(nullable = false)
    private Integer retryCount;

    @Column
    private String lastError;

    @Column
    private Instant lockedUntil;

    private OutboxEvent(UUID eventId, EventType eventType, AggregateType aggregateType, Long aggregateId, String payload) {
        validateAggregateId(aggregateId);
        validatePayload(payload);

        this.eventId = eventId;
        this.eventType = Objects.requireNonNull(eventType, "EventType cannot be null");
        this.aggregateType = Objects.requireNonNull(aggregateType, "AggregateType cannot be null");
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
        this.createdAt = Instant.now();
        this.publishedAt = null;
        this.retryCount = 0;
        this.lastError = null;
        this.lockedUntil = null;
    }

    public static OutboxEvent create(
            EventType eventType,
            AggregateType aggregateType,
            Long aggregateId,
            String payload) {
        return new OutboxEvent(UUID.randomUUID(), eventType, aggregateType, aggregateId, payload);
    }

    public static OutboxEvent createWithEventId(
            UUID eventId,
            EventType eventType,
            AggregateType aggregateType,
            Long aggregateId,
            String payload) {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        return new OutboxEvent(eventId, eventType, aggregateType, aggregateId, payload);
    }

    public void claim(Instant now, long leaseDurationSeconds) {
        validateDate(now);

        if (leaseDurationSeconds <= 0) {
            throw new IllegalArgumentException("leaseDurationSeconds must be greater than zero");
        }

        if (this.status == OutboxEventStatus.PUBLISHED) {
            throw new IllegalStateException("Cannot claim an event that is already PUBLISHED. EventId: " + this.eventId);
        }

        if (this.status == OutboxEventStatus.PROCESSING && !isLeaseExpired(now)) {
            throw new IllegalStateException("Cannot claim an event currently being processed under an active lease. EventId: " + this.eventId);
        }

        this.status = OutboxEventStatus.PROCESSING;
        this.lockedUntil = now.plusSeconds(leaseDurationSeconds);
    }

    public void markAsPublished(Instant now) {
        validateDate(now);
        if (this.status != OutboxEventStatus.PROCESSING) {
            throw new IllegalStateException("Only PROCESSING events can be marked as PUBLISHED. Current status: " + this.status + ", EventId: " + this.eventId);
        }
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = now;
        this.lockedUntil = null;
        this.lastError = null;
    }

    public void recordFailedAttempt(String errorMessage) {
        if (this.status != OutboxEventStatus.PROCESSING) {
            throw new IllegalStateException("Only PROCESSING events can record a failed attempt. EventId: " + this.eventId);
        }
        this.status = OutboxEventStatus.PENDING;
        this.retryCount++;
        this.lastError = errorMessage;
        this.lockedUntil = null;
    }

    public boolean isLeaseExpired(Instant now) {
        return this.lockedUntil != null && !now.isBefore(this.lockedUntil);
    }

    private static void validateAggregateId(Long aggregateId) {
        if (aggregateId == null || aggregateId <= 0) {
            throw new IllegalArgumentException("AggregateId must be greater than zero. Provided: " + aggregateId);
        }
    }

    private static void validatePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Payload cannot be null, empty, or blank.");
        }
    }

    private static void validateDate(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Date cannot be null.");
        }
    }
}
