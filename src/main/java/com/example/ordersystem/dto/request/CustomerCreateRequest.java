package com.example.ordersystem.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerCreateRequest(
        @NotBlank(message = "First name cannot be blank.")
        @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters.")
        String firstName,

        @NotBlank(message = "Last name cannot be blank.")
        @Size(min = 2, max = 100, message = "Last name must be between 2 and 100 characters.")
        String lastName,

        @NotBlank(message = "Email cannot be blank.")
        @Size(min = 2, max = 255, message = "Email be between 2 and 255 characters.")
        @Email(message = "Email format is invalid.")
        String email,

        @NotBlank(message = "Phone cannot be blank!")
        @Size(min = 2, max = 100, message = "Phone must be between 2 and 100 characters.")
        String phone,

        @NotBlank(message="Password cannot be blank!")
        @Size(min = 6, max = 255, message = "Password must be between 6 and 255 characters.")
        String password
) {
}
