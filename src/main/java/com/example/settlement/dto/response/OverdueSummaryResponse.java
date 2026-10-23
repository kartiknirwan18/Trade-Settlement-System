package com.example.settlement.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Overdue exposure and the invoices falling due in the configured window. */
public record OverdueSummaryResponse(
        long overdueInvoices,
        BigDecimal totalOverdueBalance,
        List<CurrencyAmountResponse> overdueBalanceByCurrency,
        long unacknowledgedAlerts,
        long overdueAlertCount,
        List<UpcomingDue> upcomingDue) {

    public record UpcomingDue(String invoiceNumber,
                              String tradeReference,
                              String buyerName,
                              String currencyCode,
                              LocalDate dueDate,
                              long daysUntilDue,
                              BigDecimal outstandingAmount) {
    }
}
