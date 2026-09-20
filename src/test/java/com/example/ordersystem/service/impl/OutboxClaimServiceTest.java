package com.example.ordersystem.service.impl;

import com.example.ordersystem.entity.OutboxEvent;
import com.example.ordersystem.enums.AggregateType;
import com.example.ordersystem.enums.EventType;
import com.example.ordersystem.enums.OutboxEventStatus;
import com.example.ordersystem.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class OutboxClaimServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutboxClaimService outboxClaimService;

    @Test
    @DisplayName("Test 1: Tek bir PENDING event claimBatch çağrıldığında PROCESSING durumuna geçmeli")
    void shouldClaimSinglePendingEvent() {
        // Arrange
        Instant beforeCall = Instant.now();
        OutboxEvent event = OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, AggregateType.PAYMENT, 100L, "{}");

        given(outboxEventRepository.findClaimableEventsForUpdate(any(Instant.class), eq(10)))
                .willReturn(List.of(event));

        // Act
        List<OutboxEvent> claimedEvents = outboxClaimService.claimBatch(10, 30);

        // Assert
        assertThat(claimedEvents).hasSize(1);
        OutboxEvent claimed = claimedEvents.get(0);

        assertThat(claimed.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(claimed.getLockedUntil()).isNotNull();
        // Exact equality yerine zaman aralığı doğrulaması (Service kendi Instant.now()'ını oluşturur)
        assertThat(claimed.getLockedUntil()).isAfter(beforeCall.plusSeconds(29));
        assertThat(claimed.getLockedUntil()).isBefore(beforeCall.plusSeconds(32));
    }

    @Test
    @DisplayName("Test 2: Repository'den dönen birden fazla event'in tamamı PROCESSING olarak claim edilmeli")
    void shouldClaimAllEventsInBatch() {
        // Arrange
        OutboxEvent event1 = OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, AggregateType.PAYMENT, 101L, "{}");
        OutboxEvent event2 = OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, AggregateType.PAYMENT, 102L, "{}");
        OutboxEvent event3 = OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, AggregateType.PAYMENT, 103L, "{}");

        given(outboxEventRepository.findClaimableEventsForUpdate(any(Instant.class), eq(10)))
                .willReturn(List.of(event1, event2, event3));

        // Act
        List<OutboxEvent> claimedEvents = outboxClaimService.claimBatch(10, 30);

        // Assert
        assertThat(claimedEvents).hasSize(3);
        assertThat(claimedEvents)
                .extracting(OutboxEvent::getStatus)
                .containsExactly(
                        OutboxEventStatus.PROCESSING,
                        OutboxEventStatus.PROCESSING,
                        OutboxEventStatus.PROCESSING
                );

        assertThat(claimedEvents)
                .allSatisfy(event -> assertThat(event.getLockedUntil()).isNotNull());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -30})
    @DisplayName("Test 3: Geçersiz lease duration gönderilirse OutboxEvent domain invariant'ı fırlatılmalı ve nesne PROCESSING olmamalı")
    void shouldFailWhenLeaseDurationIsInvalid(long invalidLease) {
        // Arrange
        OutboxEvent event = OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, AggregateType.PAYMENT, 100L, "{}");
        given(outboxEventRepository.findClaimableEventsForUpdate(any(Instant.class), eq(10)))
                .willReturn(List.of(event));

        // Act & Assert
        assertThatThrownBy(() -> outboxClaimService.claimBatch(10, invalidLease))
                .isInstanceOf(IllegalArgumentException.class);

        // Domain invariant çalıştığı için event PROCESSING durumuna geçmemiş olmalıdır
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getLockedUntil()).isNull();
    }
}
