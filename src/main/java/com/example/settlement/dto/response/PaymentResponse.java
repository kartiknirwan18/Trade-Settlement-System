package com.example.settlement.dto.response;

import com.example.settlement.enums.PaymentDirection;
import com.example.settlement.enums.PaymentMethod;
import com.example.settlement.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        String paymentReference,
        Long invoiceId,
        String invoiceNumber,
        PaymentDirection direction,
        LocalDate paymentDate,
        BigDecimal amount,
        String currencyCode,
        PaymentMethod paymentMethod,
        String transactionReference,
        PaymentStatus status,
        String notes,
        String reversalReason,
        LocalDateTime reversedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
