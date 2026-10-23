package com.example.settlement.dto.response;

import com.example.settlement.enums.InvoiceStatus;
import com.example.settlement.enums.InvoiceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        Long tradeId,
        String tradeReference,
        InvoiceType invoiceType,
        String buyerName,
        String sellerName,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String commodityDescription,
        BigDecimal quantity,
        BigDecimal unitPrice,
        String currencyCode,
        BigDecimal subtotal,
        BigDecimal taxPercentage,
        BigDecimal taxAmount,
        BigDecimal additionalCharges,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        BigDecimal amountPaid,
        BigDecimal outstandingAmount,
        InvoiceStatus status,
        boolean overdue,
        long daysPastDue,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
