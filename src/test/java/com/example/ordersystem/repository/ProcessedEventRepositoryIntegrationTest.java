package com.example.ordersystem.repository;

import com.example.ordersystem.entity.ProcessedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProcessedEventRepositoryIntegrationTest {

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("1. Event kaydedilebilir ve eventId ile sorgulanabilir")
    void shouldSaveAndFindProcessedEvent() {
        String eventId = UUID.randomUUID().toString();
        ProcessedEvent event = ProcessedEvent.create(eventId);

        ProcessedEvent saved = processedEventRepository.save(event);
        entityManager.flush();

        Optional<ProcessedEvent> found = processedEventRepository.findByEventId(eventId);
        assertThat(found).isPresent();
        assertThat(found.get().getEventId()).isEqualTo(eventId);
        assertThat(found.get().getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("2. existsByEventId var olan eventId için true dönmeli")
    void shouldReturnTrueWhenEventIdExists() {
        String eventId = UUID.randomUUID().toString();
        processedEventRepository.saveAndFlush(ProcessedEvent.create(eventId));

        boolean exists = processedEventRepository.existsByEventId(eventId);

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("3 & 4. Gerçek DB Constraints: Aynı eventId ikinci kez eklendiğinde DB UNIQUE constraint seviyesinde DataIntegrityViolationException fırlatılmalı")
    void shouldThrowDataIntegrityViolationExceptionWhenDuplicateEventIdInserted() {
        String duplicateEventId = "evt-unique-test-12345";

        // İlk kayıt başarıyla persistence context ve DB'ye yazılır
        ProcessedEvent event1 = ProcessedEvent.create(duplicateEventId);
        processedEventRepository.save(event1);
        entityManager.flush();

        // Aynı eventId ile 2. kayıt denemesi yapılıyor
        ProcessedEvent event2 = ProcessedEvent.create(duplicateEventId);

        assertThatThrownBy(() -> {
            processedEventRepository.save(event2);
            entityManager.flush(); // Exception'ın DB seviyesinde fırlatılmasını zorlar
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("5. Atomic Insert - Event ilk kez geliyor -> 1 döner ve DB'de kayıt oluşur")
    void shouldReturnOneAndSaveRecordWhenEventIsNew() {
        // Arrange
        String eventId = "evt-" + UUID.randomUUID();
        Instant now = Instant.now();

        // Act
        int insertedCount = processedEventRepository.insertIfNotExists(eventId, now);
        entityManager.flush();
        entityManager.clear();

        // Assert
        assertThat(insertedCount).isEqualTo(1);

        Optional<ProcessedEvent> savedEvent = processedEventRepository.findByEventId(eventId);
        assertThat(savedEvent).isPresent();
        assertThat(savedEvent.get().getEventId()).isEqualTo(eventId);
    }

    @Test
    @DisplayName("6. Atomic Insert - Aynı event ikinci kez geliyor -> 0 döner ve DB'de hâlâ tek kayıt bulunur")
    void shouldReturnZeroAndNotDuplicateWhenEventAlreadyExists() {
        // Arrange
        String duplicateEventId = "evt-duplicate-12345";
        Instant now = Instant.now();

        // İlk insertion -> 1 dönmeli
        int firstInsertCount = processedEventRepository.insertIfNotExists(duplicateEventId, now);
        entityManager.flush();
        assertThat(firstInsertCount).isEqualTo(1);

        // Act: Aynı eventId ile tekrar insertion denemesi
        int secondInsertCount = processedEventRepository.insertIfNotExists(duplicateEventId, now);
        entityManager.flush();

        // Assert
        assertThat(secondInsertCount).isEqualTo(0);

        // DB'de toplam sadece 1 kayıt olmalı
        Optional<ProcessedEvent> eventInDb = processedEventRepository.findByEventId(duplicateEventId);
        assertThat(eventInDb).isPresent();
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }
}