package com.example.ordersystem.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JacksonEventDeserializer implements EventDeserializer {

    private final ObjectMapper objectMapper;

    @Override
    public <T> T deserialize(String payload, Class<T> clazz) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Payload cannot be null or blank");
        }
        if (clazz == null) {
            throw new IllegalArgumentException("Target class cannot be null");
        }

        try {
            return objectMapper.readValue(payload, clazz);
        } catch (Exception e) {
            log.error("Failed to deserialize payload to class {}. Payload snippet: {}",
                    clazz.getSimpleName(), payload.length() > 100 ? payload.substring(0, 100) + "..." : payload, e);
            throw new RuntimeException("Failed to deserialize event payload to " + clazz.getName(), e);
        }
    }
}