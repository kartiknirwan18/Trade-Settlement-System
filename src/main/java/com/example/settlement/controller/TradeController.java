package com.example.settlement.controller;

import com.example.settlement.dto.common.ApiResponse;
import com.example.settlement.dto.common.PageResponse;
import com.example.settlement.dto.request.TradeCreateRequest;
import com.example.settlement.dto.request.TradeUpdateRequest;
import com.example.settlement.dto.response.InvoiceResponse;
import com.example.settlement.dto.response.TradeResponse;
import com.example.settlement.enums.TradeStatus;
import com.example.settlement.enums.TradeType;
import com.example.settlement.service.InvoiceService;
import com.example.settlement.service.TradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/trades")
@Tag(name = "Trades", description = "Creation, maintenance and search of commodity trades")
public class TradeController {

    private final TradeService tradeService;
    private final InvoiceService invoiceService;

    public TradeController(TradeService tradeService, InvoiceService invoiceService) {
        this.tradeService = tradeService;
        this.invoiceService = invoiceService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TRADER','ADMIN','FINANCE')")
    @Operation(summary = "Create a trade", description = "Registers a new trade. A unique trade reference is generated when omitted.")
    public ResponseEntity<ApiResponse<TradeResponse>> create(@Valid @RequestBody TradeCreateRequest request,
                                                            UriComponentsBuilder uriBuilder) {
        TradeResponse created = tradeService.create(request);
        return ResponseEntity.created(uriBuilder.path("/api/v1/trades/{id}").buildAndExpand(created.id()).toUri())
                .body(ApiResponse.ok("Trade created", created));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TRADER','FINANCE','AUDITOR','ADMIN')")
    @Operation(summary = "Search trades",
            description = "Filter by commodity (partial match), status, trade type and trade date range. Paginated and sortable.")
    public ResponseEntity<ApiResponse<PageResponse<TradeResponse>>> search(
            @RequestParam(required = false) String commodity,
            @RequestParam(required = false) TradeStatus status,
            @RequestParam(required = false) TradeType tradeType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDateTo,
            @PageableDefault(size = 20, sort = "tradeDate", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<TradeResponse> page = tradeService.search(
                commodity, status, tradeType, tradeDateFrom, tradeDateTo, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TRADER','FINANCE','AUDITOR','ADMIN')")
    @Operation(summary = "Get a trade by id")
    public ResponseEntity<ApiResponse<TradeResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(tradeService.getById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TRADER','ADMIN')")
    @Operation(summary = "Update a trade",
            description = "Partial update. Status changes must follow the trade state machine; cancelled trades are immutable.")
    public ResponseEntity<ApiResponse<TradeResponse>> update(@PathVariable Long id,
                                                            @Valid @RequestBody TradeUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Trade updated", tradeService.update(id, request)));
    }

    @GetMapping("/reference/{reference}")
    @PreAuthorize("hasAnyRole('TRADER','FINANCE','AUDITOR','ADMIN')")
    @Operation(summary = "Get a trade by reference number")
    public ResponseEntity<ApiResponse<TradeResponse>> getByReference(@PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.ok(tradeService.getByReference(reference)));
    }

    @GetMapping("/{id}/invoices")
    @PreAuthorize("hasAnyRole('TRADER','FINANCE','AUDITOR','ADMIN')")
    @Operation(summary = "List the invoices raised for a trade")
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> invoices(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.ok(invoiceService.findByTradeId(id)));
    }
}
