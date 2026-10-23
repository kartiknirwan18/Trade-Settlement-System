package com.example.settlement.repository.projection;

import com.example.settlement.enums.InvoiceStatus;

import java.math.BigDecimal;

/** Aggregate used by the dashboard to summarise invoices per status. */
public interface InvoiceStatusAggregate {

    InvoiceStatus getStatus();

    long getInvoiceCount();

    BigDecimal getTotalAmount();
}
