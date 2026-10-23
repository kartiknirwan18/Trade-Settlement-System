package com.example.settlement.controller;

import com.example.settlement.dto.common.ApiResponse;
import com.example.settlement.dto.common.PageResponse;
import com.example.settlement.dto.request.ReconciliationResolveRequest;
import com.example.settlement.dto.response.ReconciliationResponse;
import com.example.settlement.enums.ReconciliationStatus;
import com.example.settlement.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reconciliations")
@Tag(name = "Reconciliation", description = "Expected vs actual settlement comparison and discrepancy resolution")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/invoice/{invoiceId}/run")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @Operation(summary = "Reconcile an invoice",
            description = """
                    Compares the invoice total (expected) with the sum of completed payments in the same
                    currency (actual). Variance = actual - expected and is classified against the
                    configurable tolerance. Payments in a different currency are never compared silently
                    and force REVIEW_REQUIRED.""")
    public ResponseEntity<ApiResponse<ReconciliationResponse>> run(@PathVariable Long invoiceId) {
        return ResponseEntity.ok(ApiResponse.ok("Reconciliation completed", reconciliationService.runForInvoice(invoiceId)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN','TRADER')")
    @Operation(summary = "Search reconciliation runs")
    public ResponseEntity<ApiResponse<PageResponse<ReconciliationResponse>>> search(
            @RequestParam(required = false) Long invoiceId,
            @RequestParam(required = false) ReconciliationStatus status,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reconciliationDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reconciliationDateTo,
            @PageableDefault(size = 20, sort = "reconciliationDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(reconciliationService.search(invoiceId, status, currencyCode, resolved,
                reconciliationDateFrom, reconciliationDateTo, pageable)));
    }

    @GetMapping("/unresolved")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN')")
    @Operation(summary = "List unresolved reconciliation discrepancies")
    public ResponseEntity<ApiResponse<List<ReconciliationResponse>>> unresolved() {
        return ResponseEntity.ok(ApiResponse.ok(reconciliationService.findUnresolved()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN','TRADER')")
    @Operation(summary = "Get a reconciliation run by id")
    public ResponseEntity<ApiResponse<ReconciliationResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(reconciliationService.getById(id)));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @Operation(summary = "Resolve a reconciliation discrepancy",
            description = "Records the resolution notes and the resolving user. Resolution is one-way and audited.")
    public ResponseEntity<ApiResponse<ReconciliationResponse>> resolve(@PathVariable Long id,
                                                                      @Valid @RequestBody ReconciliationResolveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Discrepancy resolved", reconciliationService.resolve(id, request)));
    }
}
