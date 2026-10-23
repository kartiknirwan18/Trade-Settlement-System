package com.example.settlement.mapper;

import com.example.settlement.dto.response.AlertResponse;
import com.example.settlement.entity.Invoice;
import com.example.settlement.entity.OverdueAlert;

/** Converts {@link OverdueAlert} entities into API DTOs. */
public final class AlertMapper {

    private AlertMapper() {
    }

    public static AlertResponse toResponse(OverdueAlert alert) {
        Invoice invoice = alert.getInvoice();
        return new AlertResponse(
                alert.getId(),
                invoice == null ? null : invoice.getId(),
                invoice == null ? null : invoice.getInvoiceNumber(),
                invoice == null ? null : invoice.getTradeReference(),
                invoice == null ? null : invoice.getCurrencyCode(),
                alert.getAlertType(),
                alert.getSeverity(),
                alert.getMessage(),
                alert.getTriggerDate(),
                invoice == null ? null : invoice.outstandingAmount(),
                alert.isAcknowledged(),
                alert.getAcknowledgedBy(),
                alert.getAcknowledgedAt(),
                alert.getCreatedAt());
    }
}
