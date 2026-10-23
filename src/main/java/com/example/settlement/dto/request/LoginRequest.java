package com.example.settlement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest")
public record LoginRequest(

        @Schema(example = "finance", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "username is required")
        @Size(max = 60)
        String username,

        @Schema(example = "Finance@123", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "password is required")
        @Size(max = 100)
        String password) {
}
