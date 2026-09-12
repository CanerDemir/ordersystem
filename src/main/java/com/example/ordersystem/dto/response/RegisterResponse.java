package com.example.ordersystem.dto.response;

public record RegisterResponse(
        Long customerId,
        String firstName,
        String lastName,
        String email
) {
}
