package com.example.ordersystem.service.impl;

import com.example.ordersystem.entity.OutboxEvent;
import com.example.ordersystem.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxClaimService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEvent> claimBatch (int batchSize, long leaseDurationSeconds) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }

        Instant now = Instant.now();

        List<OutboxEvent> eventsToClaim = outboxEventRepository.findClaimableEventsForUpdate(now, batchSize);

        if (eventsToClaim.isEmpty()) {
            return List.of();
        }

        eventsToClaim.forEach(event -> event.claim(now, leaseDurationSeconds));
        return eventsToClaim;
    }
}
