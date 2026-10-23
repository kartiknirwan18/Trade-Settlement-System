package com.example.settlement.dto.response;

import com.example.settlement.enums.TradeStatus;
import com.example.settlement.enums.TradeType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TradeResponse(
        Long id,
        String tradeReference,
        String commodity,
        TradeType tradeType,
        String buyerName,
        String sellerName,
        BigDecimal quantity,
        String unitOfMeasure,
        BigDecimal unitPrice,
        String currencyCode,
        BigDecimal grossValue,
        LocalDate tradeDate,
        LocalDate deliveryDate,
        TradeStatus status,
        boolean invoiceEligible,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
