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

    private Instant publishedAt;

    @Column(nullable = false)
    private Integer retryCount;

    private String lastError;

    private OutboxEvent(EventType eventType, AggregateType aggregateType, Long aggregateId, String payload) {
        validateAggregateId(aggregateId);
        validatePayload(payload);

        this.eventId = UUID.randomUUID();
        this.eventType = Objects.requireNonNull(eventType, "EventType cannot be null");
        this.aggregateType = Objects.requireNonNull(aggregateType, "AggregateType cannot be null");
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
        this.createdAt = Instant.now();
        this.publishedAt = null;
        this.retryCount = 0;
        this.lastError = null;
    }

    public static OutboxEvent create(
            EventType eventType,
            AggregateType aggregateType,
            Long aggregateId,
            String payload) {
        return new OutboxEvent(eventType, aggregateType, aggregateId, payload);
    }

    public void markAsPublished() {
        if (this.status == OutboxEventStatus.PUBLISHED) {
            throw new IllegalStateException("OutboxEvent is already in PUBLISHED status. EventId: " + this.eventId);
        }
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void recordFailedAttempt(String errorMessage) {
        if (this.status == OutboxEventStatus.PUBLISHED) {
            throw new IllegalStateException("Cannot record failed attempt on a PUBLISHED OutboxEvent. EventId: " + this.eventId);
        }
        this.retryCount++;
        this.lastError = errorMessage;
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
}
