package com.example.ordersystem.dto.response;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String errorCode,
        String path,
        Map<String, String> validationErrors
) {
}
