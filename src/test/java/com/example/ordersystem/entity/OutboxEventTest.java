package com.example.ordersystem.entity;

import com.example.ordersystem.enums.AggregateType;
import com.example.ordersystem.enums.EventType;
import com.example.ordersystem.enums.OutboxEventStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxEventTest {

    @Test
    @DisplayName("1. Factory method correctly initializes default values for OutboxEvent")
    void shouldCreateOutboxEventWithCorrectDefaults() {
        // Given
        EventType eventType = EventType.PAYMENT_SUCCEEDED;
        AggregateType aggregateType = AggregateType.PAYMENT;
        Long aggregateId = 100L;
        String payload = "{\"paymentId\": 100, \"amount\": 250.00}";

        // When
        OutboxEvent event = OutboxEvent.create(eventType, aggregateType, aggregateId, payload);

        // Then
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getEventType()).isEqualTo(EventType.PAYMENT_SUCCEEDED);
        assertThat(event.getAggregateType()).isEqualTo(AggregateType.PAYMENT);
        assertThat(event.getAggregateId()).isEqualTo(100L);
        assertThat(event.getPayload()).isEqualTo(payload);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getRetryCount()).isEqualTo(0);
        assertThat(event.getLastError()).isNull();
    }

    @Test
    @DisplayName("2. PENDING event is successfully transitioned to PUBLISHED status")
    void shouldMarkAsPublishedSuccessfully() {
        // Given
        OutboxEvent event = OutboxEvent.create(
                EventType.PAYMENT_SUCCEEDED,
                AggregateType.PAYMENT,
                100L,
                "{}"
        );

        // When
        event.markAsPublished(Instant.now());

        // Then
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("3. Re-publishing an already PUBLISHED event throws IllegalStateException")
    void shouldThrowExceptionWhenPublishingAlreadyPublishedEvent() {
        // Given
        OutboxEvent event = OutboxEvent.create(
                EventType.PAYMENT_SUCCEEDED,
                AggregateType.PAYMENT,
                100L,
                "{}"
        );
        event.markAsPublished(Instant.now());

        // When & Then
        assertThatThrownBy(() -> event.markAsPublished(Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already in PUBLISHED status");
    }

    @Test
    @DisplayName("4. Failed attempt increments retryCount, sets lastError and keeps status as PENDING")
    void shouldRecordFailedAttemptCorrectly() {
        // Given
        OutboxEvent event = OutboxEvent.create(
                EventType.PAYMENT_SUCCEEDED,
                AggregateType.PAYMENT,
                100L,
                "{}"
        );
        String errorMessage = "Kafka cluster unavailable";

        // When
        event.recordFailedAttempt(errorMessage);

        // Then
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo(errorMessage);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);

        // Second failure
        event.recordFailedAttempt("Timeout connecting to broker");
        assertThat(event.getRetryCount()).isEqualTo(2);
        assertThat(event.getLastError()).isEqualTo("Timeout connecting to broker");
    }

    @Test
    @DisplayName("5. Recording failed attempt on an already PUBLISHED event throws IllegalStateException")
    void shouldThrowExceptionWhenRecordingFailedAttemptOnPublishedEvent() {
        // Given
        OutboxEvent event = OutboxEvent.create(
                EventType.PAYMENT_SUCCEEDED,
                AggregateType.PAYMENT,
                100L,
                "{}"
        );
        event.markAsPublished(Instant.now());

        // When & Then
        assertThatThrownBy(() -> event.recordFailedAttempt("Connection reset"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot record failed attempt on a PUBLISHED OutboxEvent");
    }

    @Test
    @DisplayName("6. Creating OutboxEvent with null, zero or negative aggregateId throws IllegalArgumentException")
    void shouldThrowExceptionWhenAggregateIdIsInvalid() {
        // Null AggregateId
        assertThatThrownBy(() -> OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, AggregateType.PAYMENT, null, "{\"key\":\"val\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AggregateId must be greater than zero");

        // Zero AggregateId
        assertThatThrownBy(() -> OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, AggregateType.PAYMENT, 0L, "{\"key\":\"val\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AggregateId must be greater than zero");

        // Negative AggregateId
        assertThatThrownBy(() -> OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, AggregateType.PAYMENT, -1L, "{\"key\":\"val\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AggregateId must be greater than zero");
    }

    @Test
    @DisplayName("7. Creating OutboxEvent with null, empty, or blank payload throws IllegalArgumentException")
    void shouldThrowExceptionWhenPayloadIsInvalid() {
        // Null Payload
        assertThatThrownBy(() -> OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, AggregateType.PAYMENT, 100L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payload cannot be null, empty, or blank");

        // Empty Payload
        assertThatThrownBy(() -> OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, AggregateType.PAYMENT, 100L, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payload cannot be null, empty, or blank");

        // Blank Payload (Whitespace only)
        assertThatThrownBy(() -> OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, AggregateType.PAYMENT, 100L, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payload cannot be null, empty, or blank");
    }

    // =========================================================================
    // Domain Invariants & Edge Case Validations
    // =========================================================================

    @Test
    @DisplayName("8. Invalid Creation - Null Enum parameters must throw NullPointerException")
    void shouldThrowExceptionWhenEnumParametersAreNull() {
        // Null EventType
        assertThatThrownBy(() -> OutboxEvent.create(null, AggregateType.PAYMENT, 100L, "{\"key\":\"val\"}"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("EventType cannot be null");

        // Null AggregateType
        assertThatThrownBy(() -> OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, null, 100L, "{\"key\":\"val\"}"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("AggregateType cannot be null");
    }

    @Test
    @DisplayName("9. State Invariant - PENDING event status guarantees publishedAt is null, PUBLISHED guarantees publishedAt is not null")
    void shouldEnforceStateInvariantsForPublishedAt() {
        // PENDING Status Invariant Check
        OutboxEvent event = OutboxEvent.create(
                EventType.PAYMENT_SUCCEEDED,
                AggregateType.PAYMENT,
                100L,
                "{\"key\":\"val\"}"
        );

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getPublishedAt()).isNull();

        // Transition to PUBLISHED
        event.markAsPublished(Instant.now());

        // PUBLISHED Status Invariant Check
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("10. Combined Flow - Event can be retried multiple times while PENDING and then successfully PUBLISHED")
    void shouldSupportRetryAttemptsAndSubsequentPublishing() {
        // Given
        OutboxEvent event = OutboxEvent.create(
                EventType.PAYMENT_SUCCEEDED,
                AggregateType.PAYMENT,
                100L,
                "{\"key\":\"val\"}"
        );

        // First retry attempt
        event.recordFailedAttempt("Broker connection timeout");
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("Broker connection timeout");
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getPublishedAt()).isNull();

        // Second retry attempt
        event.recordFailedAttempt("Kafka cluster unreachable");
        assertThat(event.getRetryCount()).isEqualTo(2);
        assertThat(event.getLastError()).isEqualTo("Kafka cluster unreachable");
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);

        // Event successfully published afterwards
        event.markAsPublished(Instant.now());

        // Final State Invariants Verification
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getRetryCount()).isEqualTo(2); // Retries count should be preserved
        assertThat(event.getLastError()).isEqualTo("Kafka cluster unreachable"); // Last error should be preserved
    }
}