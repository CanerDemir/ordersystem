package com.example.ordersystem.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentSucceededEventConsumerTest {

    @Mock
    private EventDeserializer eventDeserializer;

    @Mock
    private PaymentSucceededEventHandler paymentSucceededEventHandler;

    @InjectMocks
    private PaymentSucceededEventConsumer eventConsumer;

    @Test
    @DisplayName("Test 1 — Geçerli Payload: Deserialization ve Handler.handle() başarıyla çağrılmalı")
    void shouldDeserializeAndPassToHandlerWhenPayloadIsValid() {
        // Arrange
        String validPayload = "{\"eventId\":\"123e4567-e89b-12d3-a456-426614174000\",\"orderId\":100}";
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), 1L, 100L, 10L, new BigDecimal("150.00"), Instant.now()
        );

        when(eventDeserializer.deserialize(validPayload, PaymentSucceededEvent.class))
                .thenReturn(event);

        // Act
        eventConsumer.consume(validPayload);

        // Assert
        verify(eventDeserializer).deserialize(validPayload, PaymentSucceededEvent.class);
        verify(paymentSucceededEventHandler).handle(event);
    }

    @Test
    @DisplayName("Test 2 — Deserialization Failure: Deserializer exception fırlattığında exception propagate edilmeli, Handler çağrılmamalı")
    void shouldPropagateExceptionAndNotCallHandlerWhenDeserializationFails() {
        // Arrange
        String malformedPayload = "{ invalid json";
        RuntimeException deserializationException = new RuntimeException("Deserialization failed: Malformed JSON");

        when(eventDeserializer.deserialize(eq(malformedPayload), eq(PaymentSucceededEvent.class)))
                .thenThrow(deserializationException);

        // Act & Assert
        assertThatThrownBy(() -> eventConsumer.consume(malformedPayload))
                .isSameAs(deserializationException);

        verify(eventDeserializer).deserialize(malformedPayload, PaymentSucceededEvent.class);
        verify(paymentSucceededEventHandler, never()).handle(any());
    }

    @Test
    @DisplayName("Test 3 — Handler Failure: Handler exception fırlattığında swallow edilmemeli ve propagate edilmeli")
    void shouldPropagateExceptionWhenHandlerFails() {
        // Arrange
        String validPayload = "{\"eventId\":\"123e4567-e89b-12d3-a456-426614174000\",\"orderId\":100}";
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), 1L, 100L, 10L, new BigDecimal("150.00"), Instant.now()
        );

        when(eventDeserializer.deserialize(validPayload, PaymentSucceededEvent.class))
                .thenReturn(event);

        RuntimeException handlerException = new IllegalArgumentException("Order not found with id: 100");
        doThrow(handlerException).when(paymentSucceededEventHandler).handle(event);

        // Act & Assert
        assertThatThrownBy(() -> eventConsumer.consume(validPayload))
                .isSameAs(handlerException);

        verify(eventDeserializer).deserialize(validPayload, PaymentSucceededEvent.class);
        verify(paymentSucceededEventHandler).handle(event);
    }

    @Test
    @DisplayName("Test 4 — Duplicate Event Handling: Consumer duplicate ayrımı yapmaz, payload ve event'i doğrudan Handler'a iletir")
    void shouldPassDuplicateEventDirectlyToHandlerWithoutConsumerSideFilter() {
        // Arrange
        String duplicatePayload = "{\"eventId\":\"123e4567-e89b-12d3-a456-426614174000\",\"orderId\":100}";
        PaymentSucceededEvent duplicateEvent = new PaymentSucceededEvent(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), 1L, 100L, 10L, new BigDecimal("150.00"), Instant.now()
        );

        when(eventDeserializer.deserialize(duplicatePayload, PaymentSucceededEvent.class))
                .thenReturn(duplicateEvent);

        // Act
        eventConsumer.consume(duplicatePayload);

        // Assert
        verify(eventDeserializer).deserialize(duplicatePayload, PaymentSucceededEvent.class);
        verify(paymentSucceededEventHandler).handle(duplicateEvent);
    }
}