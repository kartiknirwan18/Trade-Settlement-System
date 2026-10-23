package com.example.settlement.service;

import com.example.settlement.dto.common.PageResponse;
import com.example.settlement.dto.request.ReconciliationResolveRequest;
import com.example.settlement.dto.response.ReconciliationResponse;
import com.example.settlement.entity.Reconciliation;
import com.example.settlement.enums.ReconciliationStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/** Compares expected invoice amounts with the amounts actually settled. */
public interface ReconciliationService {

    /** Runs a reconciliation for one invoice and appends the result to its history. */
    ReconciliationResponse runForInvoice(Long invoiceId);

    ReconciliationResponse getById(Long id);

    PageResponse<ReconciliationResponse> search(Long invoiceId,
                                               ReconciliationStatus status,
                                               String currencyCode,
                                               Boolean resolved,
                                               LocalDate reconciliationDateFrom,
                                               LocalDate reconciliationDateTo,
                                               Pageable pageable);

    List<ReconciliationResponse> findUnresolved();

    ReconciliationResponse resolve(Long id, ReconciliationResolveRequest request);

    /** Loads the entity or throws {@code ResourceNotFoundException}; for use by other services. */
    Reconciliation requireReconciliation(Long id);
}
