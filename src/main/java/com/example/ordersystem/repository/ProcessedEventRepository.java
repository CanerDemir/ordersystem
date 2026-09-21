package com.example.ordersystem.repository;

import com.example.ordersystem.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    boolean existsByEventId(String eventId);
    Optional<ProcessedEvent> findByEventId(String eventId);
}
