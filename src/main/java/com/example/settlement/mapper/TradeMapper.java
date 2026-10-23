package com.example.settlement.mapper;

import com.example.settlement.dto.response.TradeResponse;
import com.example.settlement.entity.Trade;

/** Converts {@link Trade} entities into API DTOs. */
public final class TradeMapper {

    private TradeMapper() {
    }

    public static TradeResponse toResponse(Trade trade) {
        return new TradeResponse(
                trade.getId(),
                trade.getTradeReference(),
                trade.getCommodity(),
                trade.getTradeType(),
                trade.getBuyerName(),
                trade.getSellerName(),
                trade.getQuantity(),
                trade.getUnitOfMeasure(),
                trade.getUnitPrice(),
                trade.getCurrencyCode(),
                trade.grossValue(),
                trade.getTradeDate(),
                trade.getDeliveryDate(),
                trade.getStatus(),
                trade.getStatus() != null && trade.getStatus().isInvoiceEligible(),
                trade.getNotes(),
                trade.getCreatedAt(),
                trade.getUpdatedAt());
    }
}
