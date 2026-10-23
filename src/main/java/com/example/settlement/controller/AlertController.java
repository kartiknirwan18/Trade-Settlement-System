package com.example.settlement.controller;

import com.example.settlement.dto.common.ApiResponse;
import com.example.settlement.dto.common.PageResponse;
import com.example.settlement.dto.response.AlertResponse;
import com.example.settlement.enums.AlertSeverity;
import com.example.settlement.enums.AlertType;
import com.example.settlement.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alerts", description = "Overdue and upcoming-due invoice alerts raised by the monitoring job")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN','TRADER')")
    @Operation(summary = "Search alerts",
            description = "Filter by acknowledgement state, severity, alert type, invoice, currency and trigger date range.")
    public ResponseEntity<ApiResponse<PageResponse<AlertResponse>>> search(
            @RequestParam(required = false) Boolean acknowledged,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) AlertType alertType,
            @RequestParam(required = false) Long invoiceId,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate triggerDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate triggerDateTo,
            @PageableDefault(size = 20, sort = "triggerDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(alertService.search(acknowledged, severity, alertType, invoiceId,
                invoiceNumber, currencyCode, triggerDateFrom, triggerDateTo, pageable)));
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN','TRADER')")
    @Operation(summary = "List active overdue alerts",
            description = "Unacknowledged OVERDUE_INVOICE alerts ordered from most severe and oldest first.")
    public ResponseEntity<ApiResponse<List<AlertResponse>>> overdue() {
        return ResponseEntity.ok(ApiResponse.ok(alertService.findActiveOverdue()));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @Operation(summary = "Acknowledge an alert",
            description = "Records who acknowledged the alert and when. Acknowledgement is recorded in the audit trail.")
    public ResponseEntity<ApiResponse<AlertResponse>> acknowledge(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Alert acknowledged", alertService.acknowledge(id)));
    }
}
