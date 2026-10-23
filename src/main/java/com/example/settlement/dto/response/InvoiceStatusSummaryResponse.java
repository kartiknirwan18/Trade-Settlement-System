package com.example.settlement.dto.response;

import com.example.settlement.enums.InvoiceStatus;

import java.math.BigDecimal;
import java.util.List;

/** Invoice counts and amounts grouped by lifecycle status. */
public record InvoiceStatusSummaryResponse(
        long totalInvoices,
        BigDecimal totalAmount,
        BigDecimal totalOutstanding,
        List<StatusBucket> byStatus) {

    public record StatusBucket(InvoiceStatus status, long invoiceCount, BigDecimal totalAmount) {
    }
}
