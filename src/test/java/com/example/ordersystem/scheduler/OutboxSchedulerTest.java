package com.example.ordersystem.scheduler;

import com.example.ordersystem.config.OutboxPublisherProperties;
import com.example.ordersystem.publisher.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class OutboxSchedulerTest {

    @Mock
    private OutboxPublisher outboxPublisher;

    private OutboxPublisherProperties properties;
    private OutboxScheduler outboxScheduler;

    private static final int EXPECTED_BATCH_SIZE = 50;
    private static final long EXPECTED_LEASE_SECONDS = 30L;

    @BeforeEach
    void setUp() {
        properties = new OutboxPublisherProperties();
        properties.setBatchSize(EXPECTED_BATCH_SIZE);
        properties.setLeaseDurationSeconds(EXPECTED_LEASE_SECONDS);
        properties.setFixedDelayMs(500L);

        outboxScheduler = new OutboxScheduler(outboxPublisher, properties);
    }

    @Test
    @DisplayName("Test 1 — Scheduler tetiklendiğinde OutboxPublisher.publishBatch doğru parametrelerle çağrılmalı")
    void shouldCallPublisherWithConfiguredParametersWhenTriggered() {
        // Act
        outboxScheduler.run();

        // Assert
        verify(outboxPublisher).publishBatch(EXPECTED_BATCH_SIZE, EXPECTED_LEASE_SECONDS);
        verifyNoMoreInteractions(outboxPublisher);
    }

    @Test
    @DisplayName("Test 2 — Publisher'ın döndürdüğü count sayısı scheduler akışını etkilememeli")
    void shouldExecuteNormalRunWhenPublisherReturnsCount() {
        // Arrange
        given(outboxPublisher.publishBatch(EXPECTED_BATCH_SIZE, EXPECTED_LEASE_SECONDS)).willReturn(5);

        // Act & Assert
        assertThatCode(() -> outboxScheduler.run())
                .doesNotThrowAnyException();

        verify(outboxPublisher).publishBatch(EXPECTED_BATCH_SIZE, EXPECTED_LEASE_SECONDS);
    }

    @Test
    @DisplayName("Test 3 — Publisher RuntimeException fırlattığında Scheduler exception'ı dışarı sızdırmamalı")
    void shouldCatchExceptionAndNotPropagateWhenPublisherFails() {
        // Arrange
        willThrow(new RuntimeException("Database connection timeout during claimBatch"))
                .given(outboxPublisher).publishBatch(EXPECTED_BATCH_SIZE, EXPECTED_LEASE_SECONDS);

        // Act & Assert
        assertThatCode(() -> outboxScheduler.run())
                .doesNotThrowAnyException();

        verify(outboxPublisher).publishBatch(EXPECTED_BATCH_SIZE, EXPECTED_LEASE_SECONDS);
    }

    @Test
    @DisplayName("Test 4 — Configuration değerlerinin dinamik olarak properties'ten geldiği doğrulanmalı")
    void shouldReadDynamicValuesFromProperties() {
        // Arrange
        properties.setBatchSize(200);
        properties.setLeaseDurationSeconds(120L);

        // Act
        outboxScheduler.run();

        // Assert
        verify(outboxPublisher).publishBatch(200, 120L);
    }
}