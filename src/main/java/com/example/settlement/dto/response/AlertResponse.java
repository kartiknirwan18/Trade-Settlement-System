package com.example.settlement.dto.response;

import com.example.settlement.enums.AlertSeverity;
import com.example.settlement.enums.AlertType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AlertResponse(
        Long id,
        Long invoiceId,
        String invoiceNumber,
        String tradeReference,
        String currencyCode,
        AlertType alertType,
        AlertSeverity severity,
        String message,
        LocalDate triggerDate,
        BigDecimal outstandingAmount,
        boolean acknowledged,
        String acknowledgedBy,
        LocalDateTime acknowledgedAt,
        LocalDateTime createdAt) {
}
