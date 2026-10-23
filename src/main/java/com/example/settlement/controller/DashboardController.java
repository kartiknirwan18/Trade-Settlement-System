package com.example.settlement.controller;

import com.example.settlement.dto.common.ApiResponse;
import com.example.settlement.dto.response.DashboardSummaryResponse;
import com.example.settlement.dto.response.InvoiceStatusSummaryResponse;
import com.example.settlement.dto.response.OverdueSummaryResponse;
import com.example.settlement.dto.response.PaymentSummaryResponse;
import com.example.settlement.dto.response.ReconciliationSummaryResponse;
import com.example.settlement.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Operational aggregations over trades, invoices, payments, alerts and reconciliations")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN','TRADER')")
    @Operation(summary = "Overall settlement summary",
            description = "Key counters and totals across every module. Monetary totals are grouped by currency.")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.summary()));
    }

    @GetMapping("/invoice-status")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN','TRADER')")
    @Operation(summary = "Invoices grouped by status")
    public ResponseEntity<ApiResponse<InvoiceStatusSummaryResponse>> invoiceStatus() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.invoiceStatus()));
    }

    @GetMapping("/payment-summary")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN','TRADER')")
    @Operation(summary = "Payment activity by status and direction")
    public ResponseEntity<ApiResponse<PaymentSummaryResponse>> paymentSummary() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.paymentSummary()));
    }

    @GetMapping("/overdue-summary")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN','TRADER')")
    @Operation(summary = "Overdue exposure and upcoming payment due dates",
            description = "Upcoming due dates are limited to settlement.overdue.upcoming-due-days.")
    public ResponseEntity<ApiResponse<OverdueSummaryResponse>> overdueSummary() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.overdueSummary()));
    }

    @GetMapping("/reconciliation-summary")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN','TRADER')")
    @Operation(summary = "Reconciliation outcome distribution and net variance")
    public ResponseEntity<ApiResponse<ReconciliationSummaryResponse>> reconciliationSummary() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.reconciliationSummary()));
    }
}
