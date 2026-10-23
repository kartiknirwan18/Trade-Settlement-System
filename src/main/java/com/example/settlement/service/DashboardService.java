package com.example.settlement.service;

import com.example.settlement.dto.response.DashboardSummaryResponse;
import com.example.settlement.dto.response.InvoiceStatusSummaryResponse;
import com.example.settlement.dto.response.OverdueSummaryResponse;
import com.example.settlement.dto.response.PaymentSummaryResponse;
import com.example.settlement.dto.response.ReconciliationSummaryResponse;

/** Read-only aggregations powering the operational dashboard. */
public interface DashboardService {

    DashboardSummaryResponse summary();

    InvoiceStatusSummaryResponse invoiceStatus();

    PaymentSummaryResponse paymentSummary();

    OverdueSummaryResponse overdueSummary();

    ReconciliationSummaryResponse reconciliationSummary();
}
