package com.example.ordersystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @Size(max = 100, message = "Address title cannot exceed 100 characters.")
        String title,

        @NotBlank(message = "City cannot be blank!")
        @Size(min = 2, max = 100, message = "City must be between 2 and 100 characters.")
        String city,

        @NotBlank(message = "District cannot be blank!")
        @Size(min = 2, max = 100, message = "District must be between 2 and 100 characters.")
        String district,

        @Size(max = 20, message = "Zip code cannot exceed 20 characters.")
        String zipCode,

        @NotBlank(message = "Country cannot be blank!")
        @Size(min = 2, max = 100, message = "Country must be between 2 and 100 characters.")
        String country,

        @NotBlank(message = "Address line cannot be blank!")
        @Size(min = 2, max = 500, message = "Address line must be between 2 and 500 characters.")
        String addressLine,

        @Size(max = 500, message = "Address detail cannot exceed 500 characters.")
        String addressDetail
) {
}
