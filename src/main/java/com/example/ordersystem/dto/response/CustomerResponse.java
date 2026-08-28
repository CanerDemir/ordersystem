package com.example.ordersystem.dto.response;

public record CustomerResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone
) {}