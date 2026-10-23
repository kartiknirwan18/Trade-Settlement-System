package com.example.settlement.dto.request;

import com.example.settlement.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Administrative payload used to create an application user. */
public record RegisterUserRequest(

        @Schema(example = "jdoe", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "username is required")
        @Size(min = 3, max = 60)
        String username,

        @Schema(example = "jdoe@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 150)
        String email,

        @Schema(description = "Minimum 8 characters", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 100, message = "password must be between 8 and 100 characters")
        String password,

        @Schema(example = "Jane Doe")
        @Size(max = 150)
        String fullName,

        @Schema(example = "FINANCE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "role is required")
        Role role) {
}
