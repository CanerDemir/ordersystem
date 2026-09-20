package com.example.ordersystem.repository;

import com.example.ordersystem.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = """
        SELECT * FROM outbox_events e
        WHERE e.status = 'PENDING'
            OR (e.status = 'PROCESSING' AND e.locked_until <= :now)
        ORDER BY e.created_at ASC, e.id ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """,  nativeQuery = true)
    List<OutboxEvent> findClaimableEventsForUpdate(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
