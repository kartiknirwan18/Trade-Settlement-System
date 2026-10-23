package com.example.settlement.service;

import com.example.settlement.dto.common.PageResponse;
import com.example.settlement.dto.request.InvoiceGenerateRequest;
import com.example.settlement.dto.response.InvoiceResponse;
import com.example.settlement.dto.response.OutstandingResponse;
import com.example.settlement.entity.Invoice;
import com.example.settlement.enums.InvoiceStatus;
import com.example.settlement.enums.InvoiceType;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/** Invoice generation, lifecycle and outstanding balance operations. */
public interface InvoiceService {

    /** Generates an invoice for a completed trade. */
    InvoiceResponse generateFromTrade(Long tradeId, InvoiceGenerateRequest request);

    InvoiceResponse getById(Long id);

    InvoiceResponse getByNumber(String invoiceNumber);

    PageResponse<InvoiceResponse> search(String invoiceNumber,
                                        String tradeReference,
                                        InvoiceStatus status,
                                        InvoiceType invoiceType,
                                        String currencyCode,
                                        Long tradeId,
                                        LocalDate invoiceDateFrom,
                                        LocalDate invoiceDateTo,
                                        Pageable pageable);

    InvoiceResponse issue(Long id);

    InvoiceResponse cancel(Long id, String reason);

    OutstandingResponse getOutstanding(Long id);

    List<InvoiceResponse> findOverdue();

    List<InvoiceResponse> findByTradeId(Long tradeId);

    /** Plain-text printable representation of the invoice. */
    String renderPrintable(Long id);

    /** Loads the entity or throws {@code ResourceNotFoundException}; for use by other services. */
    Invoice requireInvoice(Long id);

    /**
     * Recomputes {@code amountPaid} from completed payments and derives the
     * invoice status. Must be called inside the caller's transaction so that the
     * payment and the balance update commit atomically.
     */
    Invoice refreshSettlementState(Long invoiceId);

    /**
     * Flags every invoice that is past due with a positive balance as OVERDUE and
     * returns all of them so that alerting can act on the refreshed state.
     */
    List<Invoice> refreshOverdueInvoices(LocalDate asOf);
}
