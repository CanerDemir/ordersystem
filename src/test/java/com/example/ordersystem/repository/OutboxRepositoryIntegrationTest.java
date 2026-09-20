package com.example.ordersystem.repository;

import com.example.ordersystem.entity.OutboxEvent;
import com.example.ordersystem.enums.AggregateType;
import com.example.ordersystem.enums.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // Gerçek PostgreSQL kullanıyoruz
@ActiveProfiles("test")
class OutboxRepositoryIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxRepository;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
    }

    @Test
    @DisplayName("PENDING eventler ve süresi dolmuş PROCESSING eventler getirilmeli; aktif ve PUBLISHED olanlar getirilmemeli")
    void shouldFetchOnlyEligibleEventsForClaiming() {
        Instant now = Instant.now();

        // 1. Claim edilebilir: PENDING
        OutboxEvent pendingEvent = OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, AggregateType.PAYMENT, 101L, "{}");

        // 2. Claim edilebilir: Expired PROCESSING
        OutboxEvent expiredProcessingEvent = OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, AggregateType.PAYMENT, 102L, "{}");
        expiredProcessingEvent.claim(now.minusSeconds(100), 30); // lockedUntil = now - 70s (EXPIRED)

        // 3. CLAIM EDİLEMEZ: Active PROCESSING
        OutboxEvent activeProcessingEvent = OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, AggregateType.PAYMENT, 103L, "{}");
        activeProcessingEvent.claim(now, 300); // lockedUntil = now + 300s (ACTIVE)

        // 4. CLAIM EDİLEMEZ: PUBLISHED
        OutboxEvent publishedEvent = OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, AggregateType.PAYMENT, 104L, "{}");
        publishedEvent.claim(now.minusSeconds(50), 30);
        publishedEvent.markAsPublished(now.minusSeconds(10));

        outboxRepository.saveAll(List.of(pendingEvent, expiredProcessingEvent, activeProcessingEvent, publishedEvent));

        // Act
        List<OutboxEvent> claimable = outboxRepository.findClaimableEventsForUpdate(now, 10);

        // Assert
        assertThat(claimable).hasSize(2);
        assertThat(claimable)
                .extracting(OutboxEvent::getAggregateId)
                .containsExactlyInAnyOrder(101L, 102L);
    }

    @Test
    @DisplayName("Sorgu, istenen batchSize sınırına kesin olarak uymalıdır")
    void shouldRespectBatchSizeLimit() {
        Instant now = Instant.now();

        // 5 adet PENDING event oluştur
        for (long i = 1; i <= 5; i++) {
            outboxRepository.save(OutboxEvent.create(EventType.PAYMENT_SUCCEEDED, AggregateType.PAYMENT, i, "{}"));
        }

        // batchSize = 3 ile çağır
        List<OutboxEvent> claimable = outboxRepository.findClaimableEventsForUpdate(now, 3);

        assertThat(claimable).hasSize(3);
    }
}