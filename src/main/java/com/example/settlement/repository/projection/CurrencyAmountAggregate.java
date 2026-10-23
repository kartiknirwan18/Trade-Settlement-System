package com.example.settlement.repository.projection;

import java.math.BigDecimal;

/** Aggregate of a monetary total grouped by ISO currency code. */
public interface CurrencyAmountAggregate {

    String getCurrencyCode();

    BigDecimal getTotalAmount();
}
