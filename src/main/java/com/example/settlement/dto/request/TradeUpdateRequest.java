package com.example.settlement.dto.request;

import com.example.settlement.enums.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Partial update payload for a trade. Only non-null fields are applied.
 * Status changes go through {@link TradeStatusUpdateRequest} semantics but are
 * also accepted here for convenience.
 */
@Schema(name = "TradeUpdateRequest", description = "Partial update of an existing trade")
public record TradeUpdateRequest(

        @Size(max = 120)
        String commodity,

        TradeType tradeType,

        @Size(max = 150)
        String buyerName,

        @Size(max = 150)
        String sellerName,

        @DecimalMin(value = "0.0", inclusive = false, message = "quantity must be greater than zero")
        @Digits(integer = 15, fraction = 4)
        BigDecimal quantity,

        @Size(max = 20)
        String unitOfMeasure,

        @DecimalMin(value = "0.0", inclusive = false, message = "unitPrice must be greater than zero")
        @Digits(integer = 15, fraction = 4)
        BigDecimal unitPrice,

        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currencyCode must be a 3 letter ISO 4217 code")
        String currencyCode,

        LocalDate tradeDate,

        LocalDate deliveryDate,

        @Schema(description = "New status; must be a legal transition from the current status")
        com.example.settlement.enums.TradeStatus status,

        @Size(max = 500)
        String notes) {
}
