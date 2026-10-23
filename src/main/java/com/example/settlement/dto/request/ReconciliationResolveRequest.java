package com.example.settlement.dto.request;

import com.example.settlement.enums.ReconciliationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Resolution decision recorded against an unresolved reconciliation discrepancy. */
public record ReconciliationResolveRequest(

        @Schema(description = "How the discrepancy was resolved", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Short payment of 120.00 written off as bank charges, approved by Finance Manager")
        @NotBlank(message = "resolutionNotes is required")
        @Size(max = 500)
        String resolutionNotes,

        @Schema(description = "Optional explicit status to set. When omitted the record is simply marked resolved.",
                example = "MATCHED")
        ReconciliationStatus status) {
}
