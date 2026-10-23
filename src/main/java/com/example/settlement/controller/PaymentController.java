package com.example.settlement.controller;

import com.example.settlement.dto.common.ApiResponse;
import com.example.settlement.dto.common.PageResponse;
import com.example.settlement.dto.request.PaymentCreateRequest;
import com.example.settlement.dto.request.PaymentReverseRequest;
import com.example.settlement.dto.response.PaymentResponse;
import com.example.settlement.enums.PaymentDirection;
import com.example.settlement.enums.PaymentStatus;
import com.example.settlement.service.PaymentService;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment capture, partial settlement, completion and reversal")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @Operation(summary = "Record a payment against an invoice",
            description = """
                    The payment direction must match the invoice type (RECEIVABLE -> INCOMING,
                    PAYABLE -> OUTGOING) and the currency must match the invoice currency.
                    Payments are created as PENDING unless status=COMPLETED is supplied; only
                    COMPLETED payments reduce the outstanding balance. Overpayment is rejected
                    unless settlement.payment.allow-overpayment is enabled.""")
    public ResponseEntity<ApiResponse<PaymentResponse>> create(@Valid @RequestBody PaymentCreateRequest request,
                                                              UriComponentsBuilder uriBuilder) {
        PaymentResponse created = paymentService.create(request);
        return ResponseEntity.created(uriBuilder.path("/api/v1/payments/{id}").buildAndExpand(created.id()).toUri())
                .body(ApiResponse.ok("Payment recorded", created));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN','TRADER')")
    @Operation(summary = "Search payments",
            description = "Filter by invoice, payment reference, status, direction, currency, counterparty name and payment date range.")
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> search(
            @RequestParam(required = false) Long invoiceId,
            @RequestParam(required = false) String paymentReference,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentDirection direction,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) String counterparty,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDateTo,
            @PageableDefault(size = 20, sort = "paymentDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.search(invoiceId, paymentReference, status, direction,
                currencyCode, counterparty, paymentDateFrom, paymentDateTo, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','ADMIN','TRADER')")
    @Operation(summary = "Get a payment by id")
    public ResponseEntity<ApiResponse<PaymentResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getById(id)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @Operation(summary = "Complete a pending payment",
            description = "Moves PENDING -> COMPLETED and reduces the invoice outstanding balance.")
    public ResponseEntity<ApiResponse<PaymentResponse>> complete(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Payment completed", paymentService.complete(id)));
    }

    @PostMapping("/{id}/fail")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @Operation(summary = "Mark a pending payment as failed")
    public ResponseEntity<ApiResponse<PaymentResponse>> fail(@PathVariable Long id,
                                                            @Valid @RequestBody PaymentReverseRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Payment marked failed", paymentService.fail(id, request.reason())));
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @Operation(summary = "Reverse a completed payment",
            description = """
                    The payment row is preserved with status REVERSED and the reversal reason is
                    captured in the audit trail; the invoice outstanding balance is recalculated.""")
    public ResponseEntity<ApiResponse<PaymentResponse>> reverse(@PathVariable Long id,
                                                               @Valid @RequestBody PaymentReverseRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Payment reversed", paymentService.reverse(id, request.reason())));
    }
}
