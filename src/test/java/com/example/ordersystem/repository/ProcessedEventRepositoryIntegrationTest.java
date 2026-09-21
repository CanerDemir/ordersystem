package com.example.ordersystem.repository;

import com.example.ordersystem.entity.ProcessedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

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
}