package com.example.settlement.dto.response;

import java.util.List;

/** Outcome distribution of reconciliation runs and the net variance by currency. */
public record ReconciliationSummaryResponse(
        long totalRuns,
        long matched,
        long underpaid,
        long overpaid,
        long unmatched,
        long reviewRequired,
        long unresolved,
        List<CurrencyAmountResponse> varianceByCurrency) {
}
