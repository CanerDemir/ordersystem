package com.example.ordersystem.dto;

public record PaymentResultDto(
        boolean successful,
        String transactionReference,
        String errorMessage
) {
    public static PaymentResultDto success(String transactionReference) {
        return new PaymentResultDto(true, transactionReference, null);
    }

    public static PaymentResultDto failure(String errorMessage) {
        return new PaymentResultDto(false, null, errorMessage);
    }
}
