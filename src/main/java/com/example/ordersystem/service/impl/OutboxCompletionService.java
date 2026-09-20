package com.example.ordersystem.service.impl;

import com.example.ordersystem.entity.OutboxEvent;
import com.example.ordersystem.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxCompletionService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsPublished(Long eventId) {
        Instant now = Instant.now();
        OutboxEvent event = outboxEventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("OutboxEvent not found with id: " + eventId));

        event.markAsPublished(now);
        log.info("Outbox event successfully marked as PUBLISHED. eventId={}", eventId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long eventId, Throwable throwable) {
        OutboxEvent event = outboxEventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("OutboxEvent not found with id: " + eventId));

        String errorMessage = throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();

        event.recordFailedAttempt(errorMessage);

        log.warn("Outbox event delivery failed. eventId={}, retryCount={}, error={}",
                eventId, event.getRetryCount(), errorMessage);

    }
}
