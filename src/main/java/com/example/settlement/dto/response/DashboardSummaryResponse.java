package com.example.settlement.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** High-level operational counters for the landing dashboard. */
public record DashboardSummaryResponse(
        long totalTrades,
        long completedTrades,
        long totalInvoices,
        long openInvoices,
        long paidInvoices,
        long overdueInvoices,
        long totalPayments,
        long completedPayments,
        long unresolvedDiscrepancies,
        long unacknowledgedAlerts,
        BigDecimal totalInvoiced,
        BigDecimal totalSettled,
        List<CurrencyAmountResponse> totalInvoicedByCurrency,
        List<CurrencyAmountResponse> outstandingByCurrency) {
}
