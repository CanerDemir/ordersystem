package com.example.ordersystem.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JacksonEventSerializer implements EventSerializer {

    private final ObjectMapper objectMapper;

    @Override
    public <T> String serialize(T event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event: " + event.getClass().getName(), e);
        }
    }
}