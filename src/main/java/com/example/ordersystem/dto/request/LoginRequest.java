package com.example.ordersystem.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email cannot be blank.")
        @Size(min = 2, max = 255, message = "Email must be between 2 and 255 characters.")
        @Email(message = "Email format is invalid.")
        String email,

        @NotBlank(message="Password cannot be blank!")
        String password
) {
}
