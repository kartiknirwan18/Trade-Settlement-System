package com.example.settlement.mapper;

import com.example.settlement.dto.response.PaymentResponse;
import com.example.settlement.entity.Invoice;
import com.example.settlement.entity.Payment;

/** Converts {@link Payment} entities into API DTOs. */
public final class PaymentMapper {

    private PaymentMapper() {
    }

    public static PaymentResponse toResponse(Payment payment) {
        Invoice invoice = payment.getInvoice();
        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentReference(),
                invoice == null ? null : invoice.getId(),
                invoice == null ? null : invoice.getInvoiceNumber(),
                payment.getDirection(),
                payment.getPaymentDate(),
                payment.getAmount(),
                payment.getCurrencyCode(),
                payment.getPaymentMethod(),
                payment.getTransactionReference(),
                payment.getStatus(),
                payment.getNotes(),
                payment.getReversalReason(),
                payment.getReversedAt(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}
