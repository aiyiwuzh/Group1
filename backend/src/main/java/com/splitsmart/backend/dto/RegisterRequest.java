
package com.splitsmart.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(

        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        @Pattern(
                regexp = "(?i)^[A-Z0-9._%+-]+@usc\\.edu$",
                message = "Must use a USC email address"
        )
        String email,

        @NotBlank(message = "USC ID is required")
        @Pattern(
                regexp = "^[0-9]{10}$",
                message = "USC ID must contain exactly 10 digits"
        )
        String uscId,

        @NotBlank(message = "Password is required")
        String password
) {}
