package com.example.settlement.dto.response;

import java.util.List;

/** Settlement activity grouped by payment status and direction. */
public record PaymentSummaryResponse(
        long totalPayments,
        long completedPayments,
        long pendingPayments,
        long failedPayments,
        long reversedPayments,
        List<CurrencyAmountResponse> incomingByCurrency,
        List<CurrencyAmountResponse> outgoingByCurrency) {
}
