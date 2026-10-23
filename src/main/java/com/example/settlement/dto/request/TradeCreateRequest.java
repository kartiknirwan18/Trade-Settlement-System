package com.example.settlement.dto.request;

import com.example.settlement.enums.TradeStatus;
import com.example.settlement.enums.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "TradeCreateRequest", description = "Payload used to register a commodity trade")
public record TradeCreateRequest(

        @Schema(description = "Optional client supplied trade reference. Generated when omitted.",
                example = "TRD-20260115-8KQ2ZP")
        @Size(max = 40, message = "tradeReference must not exceed 40 characters")
        String tradeReference,

        @Schema(description = "Commodity being traded", example = "Brent Crude Oil", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "commodity is required")
        @Size(max = 120, message = "commodity must not exceed 120 characters")
        String commodity,

        @Schema(example = "BUY", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "tradeType is required")
        TradeType tradeType,

        @Schema(example = "Shell Trading Ltd", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "buyerName is required")
        @Size(max = 150)
        String buyerName,

        @Schema(example = "BP Energy", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "sellerName is required")
        @Size(max = 150)
        String sellerName,

        @Schema(description = "Must be strictly positive", example = "50000.0000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "quantity is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "quantity must be greater than zero")
        @Digits(integer = 15, fraction = 4, message = "quantity supports at most 15 integer and 4 fraction digits")
        BigDecimal quantity,

        @Schema(description = "Unit of measure code", example = "BARREL", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "unitOfMeasure is required")
        @Size(max = 20)
        String unitOfMeasure,

        @Schema(description = "Must be strictly positive", example = "82.4500", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "unitPrice is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "unitPrice must be greater than zero")
        @Digits(integer = 15, fraction = 4)
        BigDecimal unitPrice,

        @Schema(description = "ISO 4217 currency code", example = "USD", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "currencyCode is required")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currencyCode must be a 3 letter ISO 4217 code")
        String currencyCode,

        @Schema(example = "2026-01-15", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "tradeDate is required")
        LocalDate tradeDate,

        @Schema(description = "Defaults to tradeDate when omitted", example = "2026-02-01")
        LocalDate deliveryDate,

        @Schema(description = "Defaults to DRAFT when omitted", example = "DRAFT")
        TradeStatus status,

        @Schema(example = "Spot cargo, Incoterms FOB")
        @Size(max = 500)
        String notes) {
}
