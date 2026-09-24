package com.example.ordersystem.repository;

import com.example.ordersystem.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    boolean existsByEventId(String eventId);
    Optional<ProcessedEvent> findByEventId(String eventId);

    /**
     * PostgreSQL ON CONFLICT DO NOTHING mantığı ile atomic event insertion.
     *
     * @return Etkilenen satır sayısı:
     *         1 -> Event ilk kez işleniyor (INSERT başarılı).
     *         0 -> Event daha önce başka bir thread/consumer tarafından işlendi (CONFLICT -> IGNORED).
     */
    @Modifying
    @Query(value = """
            INSERT INTO processed_events (id, event_id, processed_at)
            VALUES (nextval('processed_event_seq'), :eventId, :processedAt)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfNotExists(@Param("eventId")  String eventId, @Param("processedAt") Instant processedAt);
}
