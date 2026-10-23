package com.example.settlement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reason supplied when a completed payment is reversed or a pending payment is marked failed. */
public record PaymentReverseRequest(

        @Schema(example = "Returned by bank - beneficiary account closed",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "reason is required")
        @Size(max = 500)
        String reason) {
}
