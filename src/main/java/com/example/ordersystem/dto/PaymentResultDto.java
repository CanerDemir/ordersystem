package com.example.ordersystem.dto;

public record PaymentResultDto(
        boolean successful,
        String transactionReference,
        String errorMessage
) {
}
