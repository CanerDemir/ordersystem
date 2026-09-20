package com.example.ordersystem.publisher;

import com.example.ordersystem.entity.OutboxEvent;
import com.example.ordersystem.enums.AggregateType;
import com.example.ordersystem.enums.EventType;
import com.example.ordersystem.service.impl.OutboxClaimService;
import com.example.ordersystem.service.impl.OutboxCompletionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxClaimService claimService;

    @Mock
    private OutboxEventPublisher eventPublisher;

    @Mock
    private OutboxCompletionService completionService;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    private static final int BATCH_SIZE = 10;
    private static final long LEASE_SECONDS = 60;

    private OutboxEvent createSampleEvent(Long id) {
        OutboxEvent event = OutboxEvent.create(
                EventType.PAYMENT_SUCCEEDED,
                AggregateType.PAYMENT,
                100L + id,
                "{\"orderId\": " + (100L + id) + "}"
        );
        setEntityId(event, id);
        return event;
    }

    private void setEntityId(OutboxEvent event, Long id) {
        try {
            Field field = OutboxEvent.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(event, id);
        } catch (Exception e) {
            throw new RuntimeException("Reflective ID assignment failed", e);
        }
    }

    @Test
    @DisplayName("1. Empty Batch: Claim edilen event yoksa broker ve completion çağrılmamalı, 0 dönmeli")
    void shouldReturnZeroAndNotInteractWhenBatchIsEmpty() {
        // Arrange
        given(claimService.claimBatch(BATCH_SIZE, LEASE_SECONDS)).willReturn(Collections.emptyList());

        // Act
        int publishedCount = outboxPublisher.publishBatch(BATCH_SIZE, LEASE_SECONDS);

        // Assert
        assertThat(publishedCount).isEqualTo(0);
        verifyNoInteractions(eventPublisher, completionService);
    }

    @Test
    @DisplayName("2. Single Event Success: Tek event başarıyla publish edilip markAsPublished çağrılmalı")
    void shouldPublishAndCompleteSingleEventSuccessfully() {
        // Arrange
        OutboxEvent event = createSampleEvent(1L);
        given(claimService.claimBatch(BATCH_SIZE, LEASE_SECONDS)).willReturn(List.of(event));

        // Act
        int publishedCount = outboxPublisher.publishBatch(BATCH_SIZE, LEASE_SECONDS);

        // Assert
        assertThat(publishedCount).isEqualTo(1);
        verify(eventPublisher).publish(event);
        verify(completionService).markAsPublished(1L);
        verify(completionService, never()).recordFailure(anyLong(), any());
    }

    @Test
    @DisplayName("3. Three Events Success: Tüm event'ler başarıyla işlendiğinde yayınlanan sayı 3 olmalı")
    void shouldPublishAndCompleteMultipleEventsSuccessfully() {
        // Arrange
        OutboxEvent event1 = createSampleEvent(1L);
        OutboxEvent event2 = createSampleEvent(2L);
        OutboxEvent event3 = createSampleEvent(3L);
        given(claimService.claimBatch(BATCH_SIZE, LEASE_SECONDS)).willReturn(List.of(event1, event2, event3));

        // Act
        int publishedCount = outboxPublisher.publishBatch(BATCH_SIZE, LEASE_SECONDS);

        // Assert
        assertThat(publishedCount).isEqualTo(3);
        verify(eventPublisher).publish(event1);
        verify(eventPublisher).publish(event2);
        verify(eventPublisher).publish(event3);

        verify(completionService).markAsPublished(1L);
        verify(completionService).markAsPublished(2L);
        verify(completionService).markAsPublished(3L);
        verify(completionService, never()).recordFailure(anyLong(), any());
    }

    @Test
    @DisplayName("4. Broker Failure: Broker yayınlama hatasında recordFailure çağrılmalı ve 0 dönmeli")
    void shouldRecordFailureWhenBrokerFails() {
        // Arrange
        OutboxEvent event = createSampleEvent(1L);
        given(claimService.claimBatch(BATCH_SIZE, LEASE_SECONDS)).willReturn(List.of(event));

        RuntimeException brokerException = new RuntimeException("Kafka connection timeout");
        willThrow(brokerException).given(eventPublisher).publish(event);

        // Act
        int publishedCount = outboxPublisher.publishBatch(BATCH_SIZE, LEASE_SECONDS);

        // Assert
        assertThat(publishedCount).isEqualTo(0);
        verify(eventPublisher).publish(event);
        verify(completionService).recordFailure(1L, brokerException);
        verify(completionService, never()).markAsPublished(anyLong());
    }

    @Test
    @DisplayName("5. Broker Failure Event N: Aradaki event2 broker hatası alsa bile event1 ve event3 tamamlanmalı")
    void shouldContinueProcessingBatchWhenMiddleEventFailsInBroker() {
        // Arrange
        OutboxEvent event1 = createSampleEvent(1L);
        OutboxEvent event2 = createSampleEvent(2L);
        OutboxEvent event3 = createSampleEvent(3L);
        given(claimService.claimBatch(BATCH_SIZE, LEASE_SECONDS)).willReturn(List.of(event1, event2, event3));

        RuntimeException brokerException = new RuntimeException("Broker partition full");
        willThrow(brokerException).given(eventPublisher).publish(event2);

        // Act
        int publishedCount = outboxPublisher.publishBatch(BATCH_SIZE, LEASE_SECONDS);

        // Assert
        assertThat(publishedCount).isEqualTo(2);

        // Event 1 Success
        verify(eventPublisher).publish(event1);
        verify(completionService).markAsPublished(1L);

        // Event 2 Failure
        verify(eventPublisher).publish(event2);
        verify(completionService).recordFailure(2L, brokerException);
        verify(completionService, never()).markAsPublished(2L);

        // Event 3 Success
        verify(eventPublisher).publish(event3);
        verify(completionService).markAsPublished(3L);
    }

    @Test
    @DisplayName("6. DB Completion Failure (Regression ⭐): Broker publish succeeds, completion transaction fails. recordFailure() asla ÇAĞRILMAMALI")
    void shouldNotCallRecordFailureWhenBrokerSucceedsButDbCompletionFails() {
        // Arrange
        OutboxEvent event = createSampleEvent(1L);
        given(claimService.claimBatch(BATCH_SIZE, LEASE_SECONDS)).willReturn(List.of(event));

        // Broker SUCCESS
        // DB Completion FAILED
        doThrow(new RuntimeException("Database deadlocked during markAsPublished"))
                .when(completionService).markAsPublished(1L);

        // Act
        int publishedCount = outboxPublisher.publishBatch(BATCH_SIZE, LEASE_SECONDS);

        // Assert
        assertThat(publishedCount).isEqualTo(0);
        verify(eventPublisher).publish(event);
        verify(completionService).markAsPublished(1L);

        // REGRESSION CHECK: Kafka mesajı aldığı için kesinlikle recordFailure çağrılmamalı!
        verify(completionService, never()).recordFailure(anyLong(), any());
    }

    @Test
    @DisplayName("7. Failure Recording Fails: Broker hatası sonrası recordFailure DB yazımı da çökerse exception dışarı sızmamalı, döngü kırılmamalı")
    void shouldHandleExceptionInRecordFailureGracefullyAndContinueBatch() {
        // Arrange
        OutboxEvent event1 = createSampleEvent(1L);
        OutboxEvent event2 = createSampleEvent(2L);
        given(claimService.claimBatch(BATCH_SIZE, LEASE_SECONDS)).willReturn(List.of(event1, event2));

        RuntimeException brokerException = new RuntimeException("Network issue");
        willThrow(brokerException).given(eventPublisher).publish(event1);

        // recordFailure da veritabanı hatası fırlatıyor
        doThrow(new RuntimeException("DB Connection lost while recording failure"))
                .when(completionService).recordFailure(eq(1L), any());

        // Act & Assert
        assertThatCode(() -> {
            int publishedCount = outboxPublisher.publishBatch(BATCH_SIZE, LEASE_SECONDS);
            // event1 patladı ama event2 başarıyla işlendi
            assertThat(publishedCount).isEqualTo(1);
        }).doesNotThrowAnyException();

        verify(eventPublisher).publish(event1);
        verify(completionService).recordFailure(1L, brokerException);

        // Batch devam etmeli ve event2 işlenmeli
        verify(eventPublisher).publish(event2);
        verify(completionService).markAsPublished(2L);
    }
}