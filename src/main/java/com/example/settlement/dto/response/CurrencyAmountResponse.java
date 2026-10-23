package com.example.settlement.dto.response;

import java.math.BigDecimal;

/**
 * A monetary total grouped by ISO currency. Currencies are never summed
 * together because no exchange-rate conversion is implemented.
 */
public record CurrencyAmountResponse(String currencyCode, BigDecimal amount) {
}
