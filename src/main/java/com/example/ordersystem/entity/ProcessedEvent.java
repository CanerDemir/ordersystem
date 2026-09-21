package com.example.ordersystem.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "processed_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_processed_events_event_id", columnNames = "event_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

    @Id
    @SequenceGenerator(name = "processed_event_seq", allocationSize = 50, sequenceName = "processed_event_seq")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "processed_event_seq")
    private Long id;

    @Column(nullable = false, length = 100)
    private String eventId;

    @Column(nullable = false, updatable = false)
    private Instant processedAt;

    public ProcessedEvent(String eventId, Instant processedAt) {
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    public static ProcessedEvent create(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId cannot be null or blank");
        }
        return new ProcessedEvent(
                eventId,
                Instant.now()
        );
    }
}
