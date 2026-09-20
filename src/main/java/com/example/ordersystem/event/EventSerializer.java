package com.example.ordersystem.event;

public interface EventSerializer {
    <T> String serialize(T event);
}