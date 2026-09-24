package com.example.ordersystem.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSucceededEventConsumer {

    private final EventDeserializer eventDeserializer;
    private final PaymentSucceededEventHandler paymentSucceededEventHandler;

    @KafkaListener(
            topics = "${kafka.consumer.topic}",
            groupId = "${kafka.consumer.group-id}"
    )
    public void consume(String payload) {
        log.info("Received PaymentSucceededEvent payload from Kafka");

        // 1. Deserialization step (Throws RuntimeException if payload is invalid)
        PaymentSucceededEvent event = eventDeserializer.deserialize(payload, PaymentSucceededEvent.class);

        // 2. Delegate to transactional Handler (Exceptions propagate naturally)
        paymentSucceededEventHandler.handle(event);

        log.info("Successfully processed event payload for eventId: {}", event.eventId());
    }
}
