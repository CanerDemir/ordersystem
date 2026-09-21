package com.example.ordersystem.event;

public interface EventDeserializer {
    <T> T deserialize(String payload, Class<T> clazz);
}
