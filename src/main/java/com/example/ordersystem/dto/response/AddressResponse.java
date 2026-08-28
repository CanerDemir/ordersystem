package com.example.ordersystem.dto.response;

public record AddressResponse(
        String title,
        String city,
        String district,
        String zipCode,
        String country,
        String addressLine,
        String addressDetail
) {
}
