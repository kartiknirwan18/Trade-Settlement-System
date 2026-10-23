package com.example.settlement.controller;

import com.example.settlement.dto.common.ApiResponse;
import com.example.settlement.dto.common.PageResponse;
import com.example.settlement.dto.request.CancelInvoiceRequest;
import com.example.settlement.dto.request.InvoiceGenerateRequest;
import com.example.settlement.dto.response.InvoiceResponse;
import com.example.settlement.dto.response.OutstandingResponse;
import com.example.settlement.dto.response.PaymentResponse;
import com.example.settlement.enums.InvoiceStatus;
import com.example.settlement.enums.InvoiceType;
import com.example.settlement.service.InvoiceService;
import com.example.settlement.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices", description = "Invoice generation, issuance, cancellation and outstanding balances")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final PaymentService paymentService;

    public InvoiceController(InvoiceService invoiceService, PaymentService paymentService) {
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
    }

    @PostMapping("/from-trade/{tradeId}")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @Operation(summary = "Generate an invoice from a completed trade",
            description = "Only COMPLETED trades are eligible. Tax percentage, charges, discount and dates can be overridden.")
    public ResponseEntity<ApiResponse<InvoiceResponse>> generateFromTrade(
            @PathVariable Long tradeId,
            @Valid @RequestBody(required = false) InvoiceGenerateRequest request,
            UriComponentsBuilder uriBuilder) {
        InvoiceResponse created = invoiceService.generateFromTrade(tradeId, request);
        return ResponseEntity.created(uriBuilder.path("/api/v1/invoices/{id}").buildAndExpand(created.id()).toUri())
                .body(ApiResponse.ok("Invoice generated", created));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN','TRADER')")
    @Operation(summary = "Search invoices",
            description = "Filter by invoice number, trade reference, status, type, currency, trade and invoice date range.")
    public ResponseEntity<ApiResponse<PageResponse<InvoiceResponse>>> search(
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String tradeReference,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) InvoiceType invoiceType,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) Long tradeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceDateTo,
            @PageableDefault(size = 20, sort = "invoiceDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(invoiceService.search(invoiceNumber, tradeReference, status,
                invoiceType, currencyCode, tradeId, invoiceDateFrom, invoiceDateTo, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN','TRADER')")
    @Operation(summary = "Get an invoice by id")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(invoiceService.getById(id)));
    }

    @GetMapping("/number/{invoiceNumber}")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN','TRADER')")
    @Operation(summary = "Get an invoice by invoice number")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getByNumber(@PathVariable String invoiceNumber) {
        return ResponseEntity.ok(ApiResponse.ok(invoiceService.getByNumber(invoiceNumber)));
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @Operation(summary = "Issue a draft invoice")
    public ResponseEntity<ApiResponse<InvoiceResponse>> issue(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Invoice issued", invoiceService.issue(id)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @Operation(summary = "Cancel an invoice",
            description = "Cancellation is rejected when any payment has already been recorded.")
    public ResponseEntity<ApiResponse<InvoiceResponse>> cancel(@PathVariable Long id,
                                                              @RequestBody(required = false) CancelInvoiceRequest request) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(ApiResponse.ok("Invoice cancelled", invoiceService.cancel(id, reason)));
    }

    @GetMapping("/{id}/outstanding")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN','TRADER')")
    @Operation(summary = "Get the outstanding balance of an invoice")
    public ResponseEntity<ApiResponse<OutstandingResponse>> outstanding(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(invoiceService.getOutstanding(id)));
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN')")
    @Operation(summary = "List overdue invoices",
            description = "Due date passed, positive outstanding balance and status not CANCELLED.")
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> overdue() {
        return ResponseEntity.ok(ApiResponse.ok(invoiceService.findOverdue()));
    }

    @GetMapping(value = "/{id}/printable", produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN','TRADER')")
    @Operation(summary = "Printable plain-text representation of an invoice")
    public ResponseEntity<String> printable(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.renderPrintable(id));
    }

    @GetMapping("/{invoiceId}/payments")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN','TRADER')")
    @Operation(summary = "Payment history for an invoice",
            description = "Returns every payment recorded against the invoice in reverse chronological order.")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> payments(@PathVariable Long invoiceId) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.findByInvoiceId(invoiceId)));
    }
}
