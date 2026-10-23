package com.example.settlement.mapper;

import com.example.settlement.dto.response.InvoiceResponse;
import com.example.settlement.dto.response.OutstandingResponse;
import com.example.settlement.entity.Invoice;
import com.example.settlement.enums.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Converts {@link Invoice} entities into API DTOs and derives overdue information. */
public final class InvoiceMapper {

    private InvoiceMapper() {
    }

    public static InvoiceResponse toResponse(Invoice invoice) {
        long daysPastDue = daysPastDue(invoice);
        boolean overdue = isOverdue(invoice);
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getTrade() == null ? null : invoice.getTrade().getId(),
                invoice.getTradeReference(),
                invoice.getInvoiceType(),
                invoice.getBuyerName(),
                invoice.getSellerName(),
                invoice.getInvoiceDate(),
                invoice.getDueDate(),
                invoice.getCommodityDescription(),
                invoice.getQuantity(),
                invoice.getUnitPrice(),
                invoice.getCurrencyCode(),
                invoice.getSubtotal(),
                invoice.getTaxPercentage(),
                invoice.getTaxAmount(),
                invoice.getAdditionalCharges(),
                invoice.getDiscountAmount(),
                invoice.getTotalAmount(),
                invoice.getAmountPaid(),
                invoice.outstandingAmount(),
                invoice.getStatus(),
                overdue,
                daysPastDue,
                invoice.getNotes(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt());
    }

    public static OutstandingResponse toOutstanding(Invoice invoice) {
        return new OutstandingResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getCurrencyCode(),
                invoice.getStatus(),
                invoice.getDueDate(),
                invoice.getTotalAmount(),
                invoice.getAmountPaid(),
                invoice.outstandingAmount(),
                isOverdue(invoice),
                daysPastDue(invoice));
    }

    /** Past due date with a strictly positive outstanding balance and not cancelled. */
    public static boolean isOverdue(Invoice invoice) {
        return invoice.getStatus() != InvoiceStatus.CANCELLED
                && invoice.getDueDate() != null
                && invoice.getDueDate().isBefore(LocalDate.now())
                && invoice.outstandingAmount().signum() > 0;
    }

    public static long daysPastDue(Invoice invoice) {
        if (!isOverdue(invoice)) {
            return 0L;
        }
        return ChronoUnit.DAYS.between(invoice.getDueDate(), LocalDate.now());
    }

    public static boolean hasBalance(Invoice invoice) {
        return invoice.outstandingAmount().compareTo(BigDecimal.ZERO) > 0;
    }
}
