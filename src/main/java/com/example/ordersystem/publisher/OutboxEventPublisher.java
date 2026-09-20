package com.example.ordersystem.publisher;

import com.example.ordersystem.entity.OutboxEvent;

public interface OutboxEventPublisher {

    /**
     * Publishes the outbox event payload to the underlying message broker.

     * Implementations are responsible for key extraction (e.g., aggregateId)
     * and routing to the target topic.
     *
     * @param event Managed or unmanaged OutboxEvent domain entity
     * @throws RuntimeException if network, serialization, or broker acknowledgment fails
     */
    void publish(OutboxEvent event);
}
