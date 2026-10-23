package com.example.settlement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Optional justification supplied when cancelling an invoice. */
public record CancelInvoiceRequest(
        @Schema(example = "Duplicate invoice raised in error", description = "Reason stored on the invoice and in the audit trail")
        @Size(max = 300)
        String reason) {
}
