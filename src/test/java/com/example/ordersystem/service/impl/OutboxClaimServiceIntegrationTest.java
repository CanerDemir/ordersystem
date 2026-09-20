package com.example.ordersystem.service.impl;

import com.example.ordersystem.entity.OutboxEvent;
import com.example.ordersystem.enums.AggregateType;
import com.example.ordersystem.enums.EventType;
import com.example.ordersystem.enums.OutboxEventStatus;
import com.example.ordersystem.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OutboxClaimServiceIntegrationTest {

    @Autowired
    private OutboxClaimService outboxClaimService;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
    }

    @Test
    @DisplayName("Service çağrısı tamamlandığında REQUIRES_NEW transaction commit edilmeli ve DB'deki event fiziksel olarak PROCESSING + locked_until ile kalıcılaşmalı")
    void shouldCommitClaimTransactionToDatabase() {
        // Arrange: DB'de 2 adet PENDING event oluştur
        OutboxEvent event1 = OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, AggregateType.PAYMENT, 201L, "{}");
        OutboxEvent event2 = OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, AggregateType.PAYMENT, 202L, "{}");
        outboxRepository.saveAll(List.of(event1, event2));

        Instant beforeClaim = Instant.now();

        // Act: OutboxClaimService (Spring proxy'si üzerinden) çağrılır
        List<OutboxEvent> claimedBatch = outboxClaimService.claimBatch(10, 60);

        // Assert: Dönen listedeki referansların kontrolü
        assertThat(claimedBatch).hasSize(2);

        // CRITICAL CHECK: Transaction kapandıktan sonra DB'yi yeniden sorgula
        List<OutboxEvent> dbEvents = outboxRepository.findAll();

        assertThat(dbEvents).hasSize(2);
        assertThat(dbEvents).allSatisfy(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
            assertThat(event.getLockedUntil()).isNotNull();
            assertThat(event.getLockedUntil()).isAfter(beforeClaim.plusSeconds(59));
        });
    }
}