package com.example.ordersystem.publisher;

import com.example.ordersystem.entity.OutboxEvent;
import com.example.ordersystem.enums.AggregateType;
import com.example.ordersystem.enums.EventType;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaOutboxEventPublisher publisher;

    private static final String TOPIC = "order-events";
    private static final long TIMEOUT_SECONDS = 2;

    @BeforeEach
    void setUp() {
        publisher = new KafkaOutboxEventPublisher(kafkaTemplate, TOPIC, TIMEOUT_SECONDS);
    }

    private OutboxEvent createTestEvent(Long id, Long aggregateId, String payload) {
        OutboxEvent event = OutboxEvent.create(
                EventType.PAYMENT_SUCCEEDED,
                AggregateType.PAYMENT,
                aggregateId,
                payload
        );
        setEventId(event, id);
        return event;
    }

    private void setEventId(OutboxEvent event, Long id) {
        try {
            Field field = OutboxEvent.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(event, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Test 1 — Success: Event doğru topic, key (String.valueOf(aggregateId)) ve value ile Kafka'ya gönderilmeli")
    void shouldPublishEventToKafkaSuccessfully() {
        // Arrange
        Long eventId = 10L;
        Long aggregateId = 123L;
        String payload = "{\"orderId\":123}";
        OutboxEvent event = createTestEvent(eventId, aggregateId, payload);

        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(TOPIC, 0), 0L, 0, 0L, 0, 0
        );
        SendResult<String, String> sendResult = new SendResult<>(null, metadata);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(sendResult);

        given(kafkaTemplate.send(TOPIC, "123", payload)).willReturn(future);

        // Act
        publisher.publish(event);

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo(TOPIC);
        assertThat(keyCaptor.getValue()).isEqualTo("123");
        assertThat(valueCaptor.getValue()).isEqualTo(payload);
    }

    @Test
    @DisplayName("Test 2 — Kafka Failure: Kafka send future'ı exception ile tamamlandığında publish() wrapped RuntimeException fırlatmalı")
    void shouldThrowRuntimeExceptionWhenFutureFails() {
        // Arrange
        OutboxEvent event = createTestEvent(10L, 123L, "{\"orderId\":123}");

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka broker unreachable"));

        given(kafkaTemplate.send(eq(TOPIC), anyString(), anyString())).willReturn(failedFuture);

        // Act & Assert
        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to publish outbox event to Kafka. eventId=10")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Test 3 — Immediate Exception: kafkaTemplate.send() çağrısı senkron exception fırlattığında exception sarmalanıp fırlatılmalı")
    void shouldThrowRuntimeExceptionWhenKafkaTemplateSendFailsImmediately() {
        // Arrange
        OutboxEvent event = createTestEvent(10L, 123L, "{\"orderId\":123}");

        given(kafkaTemplate.send(eq(TOPIC), anyString(), anyString()))
                .willThrow(new RuntimeException("Serialization exception on client side"));

        // Act & Assert
        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to initiate Kafka publish. eventId=10");
    }

    @Test
    @DisplayName("Test 4 — Event Validation: Null event geçildiğinde IllegalArgumentException fırlatılmalı")
    void shouldThrowIllegalArgumentExceptionWhenEventIsNull() {
        assertThatThrownBy(() -> publisher.publish(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("OutboxEvent cannot be null");
    }
}