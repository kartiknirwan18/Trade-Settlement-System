package com.example.settlement.dto.request;

import com.example.settlement.enums.PaymentDirection;
import com.example.settlement.enums.PaymentMethod;
import com.example.settlement.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "PaymentCreateRequest", description = "Records a payment (or expected payment) against an invoice")
public record PaymentCreateRequest(

        @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "invoiceId is required")
        Long invoiceId,

        @Schema(description = "Defaults to the direction implied by the invoice type and is validated against it",
                example = "INCOMING")
        PaymentDirection direction,

        @Schema(description = "Optional client supplied payment reference. Generated when omitted.", example = "PAY-20260120-4LM9XT")
        @Size(max = 40)
        String paymentReference,

        @Schema(example = "2026-01-20", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "paymentDate is required")
        LocalDate paymentDate,

        @Schema(description = "Must be strictly positive", example = "1000000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "amount must be greater than zero")
        @Digits(integer = 15, fraction = 4)
        BigDecimal amount,

        @Schema(description = "Defaults to the invoice currency. Must match the invoice currency.", example = "USD")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currencyCode must be a 3 letter ISO 4217 code")
        String currencyCode,

        @Schema(example = "BANK_TRANSFER", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "paymentMethod is required")
        PaymentMethod paymentMethod,

        @Schema(example = "SWIFT-REF-8891A")
        @Size(max = 80)
        String transactionReference,

        @Schema(description = "Defaults to PENDING. Only COMPLETED payments reduce the outstanding balance.",
                example = "PENDING")
        PaymentStatus status,

        @Schema(example = "Partial settlement of first cargo")
        @Size(max = 500)
        String notes) {
}
