package com.example.ordersystem.publisher;

import com.example.ordersystem.config.OutboxPublisherProperties;
import com.example.ordersystem.entity.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaOutboxEventPublisher implements OutboxEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxPublisherProperties properties;

    @Override
    public void publish(OutboxEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("OutboxEvent cannot be null");
        }

        String key = String.valueOf(event.getAggregateId());
        String value = event.getPayload();

        log.debug("Publishing outbox event to Kafka. eventId={}, aggregateId={}, topic={}, key={}",
                event.getId(), event.getAggregateId(), properties.getTopic(), key);

        try {
            // 1. Asenkron gönderim başlatılır
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(properties.getTopic(), key, value);

            // 2. Broker acknowledgement yanıtı timeout süresiyle senkron beklenir
            SendResult<String, String> result = future.get(properties.getPublishTimeoutSeconds(), TimeUnit.SECONDS);

            log.info("Successfully delivered outbox event to Kafka. eventId={}, topic={}, partition={}, offset={}",
                    event.getId(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while waiting for Kafka ACK. eventId=" + event.getId(), ex);

        } catch (ExecutionException ex) {
            // Broker, network veya serialization tarafındaki asıl hatayı sarmalar
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            throw new RuntimeException("Failed to publish outbox event to Kafka. eventId=" + event.getId(), cause);

        } catch (TimeoutException ex) {
            throw new RuntimeException("Timed out waiting for Kafka ACK. eventId=" + event.getId(), ex);

        } catch (Exception ex) {
            // kafkaTemplate.send(...) anında istisna fırlatırsa (örn: serialization/configuration hatası)
            throw new RuntimeException("Failed to initiate Kafka publish. eventId=" + event.getId(), ex);
        }
    }
}