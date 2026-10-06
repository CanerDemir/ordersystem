package com.example.ordersystem.dto;

public record RefundResultDto(
        boolean successful,
        String refundReference,
        String errorMessage
) {
    public static RefundResultDto success(String refundReference) {
        return new RefundResultDto(true, refundReference, null);
    }

    public static RefundResultDto failure(String errorMessage) {
        return new RefundResultDto(false, null, errorMessage);
    }
}
