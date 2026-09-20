package com.example.ordersystem.publisher;

import com.example.ordersystem.entity.OutboxEvent;
import com.example.ordersystem.service.impl.OutboxClaimService;
import com.example.ordersystem.service.impl.OutboxCompletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxClaimService outboxClaimService;
    private final OutboxEventPublisher outboxEventPublisher;
    private final OutboxCompletionService outboxCompletionService;

    /*
     * Orchestrates the Outbox publishing lifecycle:

     * 1. Claims next batch via isolated DB transaction (REQUIRES_NEW -> COMMIT)
     * 2. Iterates claimed events sequentially outside DB transaction
     * 3. Sends event to broker (Network Call)
     * 4. Updates completion state via isolated DB transactions (REQUIRES_NEW -> COMMIT)

     * A failure in event N does NOT prevent processing of event N+1.
     *
     * @param batchSize Max number of events to claim
     * @param leaseDurationSeconds Lease duration in seconds
     * @return Total count of successfully published events in this batch execution
     */
    public int publishBatch(int batchSize, long leaseDurationSeconds) {
        // Step 1: Claim Batch (DB Transaction 1: BEGIN -> SELECT FOR UPDATE SKIP LOCKED -> UPDATE status=PROCESSING -> COMMIT)
        List<OutboxEvent> claimedEvents = outboxClaimService.claimBatch(batchSize, leaseDurationSeconds);

        if (claimedEvents.isEmpty()) {
            return 0;
        }

        log.debug("Fetched {} claimable events for publishing", claimedEvents.size());
        int publishedCount = 0;

        // Step 2: Iterate and process outside DB transaction
        for (OutboxEvent event : claimedEvents) {
            boolean success = processSingleEvent(event);
            if (success) {
                publishedCount++;
            }
        }

        return publishedCount;
    }

    private boolean processSingleEvent(OutboxEvent event) {
        // Step 1: Message Broker Delivery (Network Boundary)
        try {
            outboxEventPublisher.publish(event);
        } catch (Exception brokerEx) {
            log.error("Broker delivery failed for outbox event. eventId={}", event.getId(), brokerEx);

            // Yalnızca broker seviyesindeki hatalarda failure kaydı tutulur ve retry handling çalışır
            safelyRecordFailure(event.getId(), brokerEx);
            return false;
        }

        // Step 2: Database Completion Update (State Boundary)
        try {
            outboxCompletionService.markAsPublished(event.getId());
            return true;
        } catch (Exception dbEx) {
            // CRITICAL: Kafka kabul etti! Buradan sonra recordFailure() ÇAĞRILMAZ.
            // Event DB'de 'PROCESSING' durumunda kalır.
            // Lease süresi dolduğunda at-least-once mekanizması re-claim eder.
            log.error("CRITICAL: Outbox event was published to Kafka, but DB completion state update failed. " +
                    "Event will remain in PROCESSING status until lease expires. eventId={}", event.getId(), dbEx);
            return false;
        }
    }

    private void safelyRecordFailure(Long eventId, Exception brokerEx) {
        try {
            outboxCompletionService.recordFailure(eventId, brokerEx);
        } catch (Exception completionEx) {
            log.error("Failed to record broker delivery failure in DB for outbox event. eventId={}", eventId, completionEx);
        }
    }
}
