package com.example.settlement.dto.request;

import com.example.settlement.enums.InvoiceType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Optional overrides applied while generating an invoice from a completed trade.
 * Every field may be omitted; sensible defaults are derived from the trade and
 * the configured business settings.
 */
public record InvoiceGenerateRequest(

        @Size(max = 300)
        String commodityDescription,

        InvoiceType invoiceType,

        LocalDate invoiceDate,

        LocalDate dueDate,

        @DecimalMin(value = "0.0", message = "taxPercentage must not be negative")
        @DecimalMax(value = "100.0", message = "taxPercentage must not exceed 100")
        @Digits(integer = 5, fraction = 4)
        BigDecimal taxPercentage,

        @DecimalMin(value = "0.0", message = "additionalCharges must not be negative")
        @Digits(integer = 15, fraction = 4)
        BigDecimal additionalCharges,

        @DecimalMin(value = "0.0", message = "discountAmount must not be negative")
        @Digits(integer = 15, fraction = 4)
        BigDecimal discountAmount,

        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currencyCode must be a 3 letter ISO 4217 code")
        String currencyCode,

        @Size(max = 500)
        String notes) {
}
