package com.example.settlement.dto.response;

import com.example.settlement.enums.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Outstanding balance view of a single invoice. */
public record OutstandingResponse(
        Long invoiceId,
        String invoiceNumber,
        String currencyCode,
        InvoiceStatus status,
        LocalDate dueDate,
        BigDecimal totalAmount,
        BigDecimal amountPaid,
        BigDecimal outstandingAmount,
        boolean overdue,
        long daysPastDue) {
}
