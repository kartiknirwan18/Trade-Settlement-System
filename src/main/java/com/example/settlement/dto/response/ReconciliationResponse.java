package com.example.settlement.dto.response;

import com.example.settlement.enums.ReconciliationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReconciliationResponse(
        Long id,
        Long invoiceId,
        String invoiceNumber,
        String currencyCode,
        BigDecimal expectedAmount,
        BigDecimal actualSettledAmount,
        BigDecimal varianceAmount,
        BigDecimal toleranceApplied,
        ReconciliationStatus status,
        LocalDate reconciliationDate,
        String notes,
        boolean resolved,
        String resolutionNotes,
        String resolvedBy,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt) {
}
