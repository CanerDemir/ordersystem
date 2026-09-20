package com.example.ordersystem.scheduler;

import com.example.ordersystem.config.OutboxPublisherProperties;
import com.example.ordersystem.publisher.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {
    private final OutboxPublisher outboxPublisher;
    private final OutboxPublisherProperties properties;

    /**
     * Spring Scheduling Trigger Adapter.
     * Uses fixedDelayString to bind the scheduling delay from externalized configuration.
     */
    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:1000}")
    public void schedulePublisherRun() {
        run();
    }

    /**
     * Core trigger action execution.
     * Isolated from Spring's @Scheduled mechanism for deterministic unit testing.
     * Guarantees exception resilience (prevents scheduler thread dying).
     */
    public void run() {
        try {
            int batchSize = properties.getBatchSize();
            long leaseDurationSeconds = properties.getLeaseDurationSeconds();

            log.trace("Triggering OutboxPublisher run with batchSize={} and leaseDurationSeconds={}",
                    batchSize, leaseDurationSeconds);

            int publishedCount = outboxPublisher.publishBatch(batchSize, leaseDurationSeconds);

            if (publishedCount > 0) {
                log.debug("OutboxScheduler cycle published {} events", publishedCount);
            }

        } catch (Exception ex) {
            // CRITICAL: Prevent top-level Exception from leaking to Spring TaskScheduler thread pool
            log.error("Unexpected error occurred in OutboxScheduler cycle. Scheduler thread recovered.", ex);
        }
    }
}
