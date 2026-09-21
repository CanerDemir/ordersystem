package com.example.ordersystem.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonEventDeserializerTest {

    private JacksonEventDeserializer deserializer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Java 8 Time Module desteği
        deserializer = new JacksonEventDeserializer(objectMapper);
    }

    @Test
    @DisplayName("Valid JSON -> Mevcut PaymentSucceededEvent contract'ına (eventId, paymentId, orderId, customerId, amount, paidAt) eksiksiz deserialize edilmeli")
    void shouldDeserializeValidJsonToPaymentSucceededEventContract() {
        // Arrange
        UUID expectedEventId = UUID.fromString("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d");
        Long expectedPaymentId = 99L;
        Long expectedOrderId = 1001L;
        Long expectedCustomerId = 505L;
        BigDecimal expectedAmount = new BigDecimal("250.50");
        Instant expectedPaidAt = Instant.parse("2026-09-21T10:00:00Z");

        String jsonPayload = String.format("""
            {
                "eventId": "%s",
                "paymentId": %d,
                "orderId": %d,
                "customerId": %d,
                "amount": %s,
                "paidAt": "%s"
            }
            """,
                expectedEventId,
                expectedPaymentId,
                expectedOrderId,
                expectedCustomerId,
                expectedAmount,
                expectedPaidAt
        );

        // Act
        PaymentSucceededEvent event = deserializer.deserialize(jsonPayload, PaymentSucceededEvent.class);

        // Assert
        assertThat(event).isNotNull();
        assertThat(event.eventId()).isEqualTo(expectedEventId);
        assertThat(event.paymentId()).isEqualTo(expectedPaymentId);
        assertThat(event.orderId()).isEqualTo(expectedOrderId);
        assertThat(event.customerId()).isEqualTo(expectedCustomerId);
        assertThat(event.amount()).isEqualByComparingTo(expectedAmount);
        assertThat(event.paidAt()).isEqualTo(expectedPaidAt);
    }

    @Test
    @DisplayName("Invalid JSON -> RuntimeException fırlatılmalı")
    void shouldThrowExceptionWhenJsonIsInvalid() {
        String invalidJson = "{ orderId: 1001, invalid_json }";

        assertThatThrownBy(() -> deserializer.deserialize(invalidJson, PaymentSucceededEvent.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to deserialize event payload");
    }

    @Test
    @DisplayName("Null veya Blank Payload -> IllegalArgumentException fırlatılmalı")
    void shouldThrowExceptionWhenPayloadIsNullOrEmpty() {
        assertThatThrownBy(() -> deserializer.deserialize(null, PaymentSucceededEvent.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payload cannot be null or blank");

        assertThatThrownBy(() -> deserializer.deserialize("   ", PaymentSucceededEvent.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payload cannot be null or blank");
    }
}