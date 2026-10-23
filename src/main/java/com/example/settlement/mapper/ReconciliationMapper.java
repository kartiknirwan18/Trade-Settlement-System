package com.example.settlement.mapper;

import com.example.settlement.dto.response.ReconciliationResponse;
import com.example.settlement.entity.Invoice;
import com.example.settlement.entity.Reconciliation;

import java.math.BigDecimal;

/** Converts {@link Reconciliation} entities into API DTOs. */
public final class ReconciliationMapper {

    private ReconciliationMapper() {
    }

    public static ReconciliationResponse toResponse(Reconciliation reconciliation, BigDecimal toleranceApplied) {
        Invoice invoice = reconciliation.getInvoice();
        return new ReconciliationResponse(
                reconciliation.getId(),
                invoice == null ? null : invoice.getId(),
                invoice == null ? null : invoice.getInvoiceNumber(),
                reconciliation.getCurrencyCode(),
                reconciliation.getExpectedAmount(),
                reconciliation.getActualSettledAmount(),
                reconciliation.getVarianceAmount(),
                toleranceApplied,
                reconciliation.getStatus(),
                reconciliation.getReconciliationDate(),
                reconciliation.getNotes(),
                reconciliation.isResolved(),
                reconciliation.getResolutionNotes(),
                reconciliation.getResolvedBy(),
                reconciliation.getResolvedAt(),
                reconciliation.getCreatedAt());
    }
}
