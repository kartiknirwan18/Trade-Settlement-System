package com.example.settlement.service.impl;

import com.example.settlement.audit.AuditActions;
import com.example.settlement.audit.AuditService;
import com.example.settlement.config.SettlementProperties;
import com.example.settlement.dto.common.PageResponse;
import com.example.settlement.dto.request.ReconciliationResolveRequest;
import com.example.settlement.dto.response.ReconciliationResponse;
import com.example.settlement.entity.Invoice;
import com.example.settlement.entity.Reconciliation;
import com.example.settlement.enums.ReconciliationStatus;
import com.example.settlement.exception.BusinessRuleException;
import com.example.settlement.exception.ResourceNotFoundException;
import com.example.settlement.mapper.ReconciliationMapper;
import com.example.settlement.repository.PaymentRepository;
import com.example.settlement.repository.ReconciliationRepository;
import com.example.settlement.security.CurrentUserProvider;
import com.example.settlement.service.InvoiceService;
import com.example.settlement.service.ReconciliationService;
import com.example.settlement.util.InvoiceCalculator;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ReconciliationServiceImpl implements ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationServiceImpl.class);
    private static final String ENTITY = "RECONCILIATION";

    private final ReconciliationRepository reconciliationRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceService invoiceService;
    private final InvoiceCalculator calculator;
    private final SettlementProperties properties;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

    public ReconciliationServiceImpl(ReconciliationRepository reconciliationRepository,
                                     PaymentRepository paymentRepository,
                                     InvoiceService invoiceService,
                                     InvoiceCalculator calculator,
                                     SettlementProperties properties,
                                     AuditService auditService,
                                     CurrentUserProvider currentUserProvider) {
        this.reconciliationRepository = reconciliationRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceService = invoiceService;
        this.calculator = calculator;
        this.properties = properties;
        this.auditService = auditService;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public ReconciliationResponse runForInvoice(Long invoiceId) {
        Invoice invoice = invoiceService.requireInvoice(invoiceId);
        int scale = calculator.scale();

        BigDecimal expected = calculator.round(invoice.getTotalAmount());
        BigDecimal actual = calculator.round(
                paymentRepository.sumCompletedByInvoiceIdAndCurrency(invoiceId, invoice.getCurrencyCode()));
        BigDecimal variance = actual.subtract(expected).setScale(scale, RoundingMode.HALF_UP);
        BigDecimal tolerance = toleranceFor(expected);
        long foreignCurrencyPayments = paymentRepository.countCompletedInOtherCurrency(invoiceId, invoice.getCurrencyCode());

        ReconciliationStatus status;
        String notes;
        if (foreignCurrencyPayments > 0) {
            status = ReconciliationStatus.REVIEW_REQUIRED;
            notes = foreignCurrencyPayments + " completed payment(s) are in a currency different from "
                    + invoice.getCurrencyCode() + " and were excluded from the settled amount. "
                    + "An explicit exchange rate conversion is required before they can be reconciled.";
        } else if (actual.signum() == 0) {
            status = ReconciliationStatus.UNMATCHED;
            notes = "No completed payments received against invoice " + invoice.getInvoiceNumber();
        } else if (variance.abs().compareTo(tolerance) <= 0) {
            status = ReconciliationStatus.MATCHED;
            notes = "Settled amount is within the configured tolerance of " + tolerance.toPlainString();
        } else if (variance.signum() < 0) {
            status = ReconciliationStatus.UNDERPAID;
            notes = "Shortfall of " + variance.abs().toPlainString() + " " + invoice.getCurrencyCode()
                    + " against expected amount " + expected.toPlainString();
        } else {
            status = ReconciliationStatus.OVERPAID;
            notes = "Excess of " + variance.toPlainString() + " " + invoice.getCurrencyCode()
                    + " above expected amount " + expected.toPlainString();
        }

        Reconciliation reconciliation = Reconciliation.builder()
                .invoice(invoice)
                .expectedAmount(expected)
                .actualSettledAmount(actual)
                .varianceAmount(variance)
                .currencyCode(invoice.getCurrencyCode())
                .reconciliationDate(LocalDate.now())
                .status(status)
                .notes(notes)
                .resolved(status == ReconciliationStatus.MATCHED)
                .build();

        Reconciliation saved = reconciliationRepository.save(reconciliation);
        ReconciliationResponse response = ReconciliationMapper.toResponse(saved, tolerance);
        auditService.record(ENTITY, saved.getId(), AuditActions.RECONCILIATION_RUN, null, response,
                "Reconciliation run for invoice " + invoice.getInvoiceNumber() + " result " + status);
        log.info("Reconciled invoice {} -> {} (expected {}, actual {}, variance {})",
                invoice.getInvoiceNumber(), status, expected, actual, variance);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationResponse getById(Long id) {
        Reconciliation reconciliation = requireReconciliation(id);
        return ReconciliationMapper.toResponse(reconciliation, toleranceFor(reconciliation.getExpectedAmount()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReconciliationResponse> search(Long invoiceId, ReconciliationStatus status, String currencyCode,
                                                       Boolean resolved, LocalDate from, LocalDate to, Pageable pageable) {
        Specification<Reconciliation> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (invoiceId != null) {
                predicates.add(cb.equal(root.get("invoice").get("id"), invoiceId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(currencyCode)) {
                predicates.add(cb.equal(root.get("currencyCode"), currencyCode.trim().toUpperCase(Locale.ROOT)));
            }
            if (resolved != null) {
                predicates.add(cb.equal(root.get("resolved"), resolved));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("reconciliationDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("reconciliationDate"), to));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Reconciliation> page = reconciliationRepository.findAll(specification, pageable);
        return PageResponse.from(page, r -> ReconciliationMapper.toResponse(r, toleranceFor(r.getExpectedAmount())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReconciliationResponse> findUnresolved() {
        return reconciliationRepository.findUnresolvedDiscrepancies().stream()
                .map(r -> ReconciliationMapper.toResponse(r, toleranceFor(r.getExpectedAmount())))
                .toList();
    }

    @Override
    @Transactional
    public ReconciliationResponse resolve(Long id, ReconciliationResolveRequest request) {
        Reconciliation reconciliation = requireReconciliation(id);
        if (reconciliation.isResolved()) {
            throw new BusinessRuleException("RECONCILIATION_ALREADY_RESOLVED",
                    "Reconciliation " + id + " has already been resolved by " + reconciliation.getResolvedBy());
        }
        String beforeStatus = reconciliation.getStatus().name();
        if (request.status() != null) {
            reconciliation.setStatus(request.status());
        }
        reconciliation.setResolved(true);
        reconciliation.setResolutionNotes(request.resolutionNotes());
        reconciliation.setResolvedBy(currentUserProvider.currentUsername());
        reconciliation.setResolvedAt(LocalDateTime.now());

        Reconciliation saved = reconciliationRepository.save(reconciliation);
        ReconciliationResponse response = ReconciliationMapper.toResponse(saved, toleranceFor(saved.getExpectedAmount()));
        auditService.record(ENTITY, saved.getId(), AuditActions.RECONCILIATION_RESOLVED,
                beforeStatus, saved.getStatus().name(),
                "Discrepancy resolved by " + saved.getResolvedBy() + ": " + saved.getResolutionNotes());
        log.info("Reconciliation {} resolved by {}", id, saved.getResolvedBy());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Reconciliation requireReconciliation(Long id) {
        return reconciliationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reconciliation", id));
    }

    /**
     * Matching tolerance = absolute tolerance + (expected amount x tolerance percentage / 100).
     */
    private BigDecimal toleranceFor(BigDecimal expectedAmount) {
        SettlementProperties.Reconciliation config = properties.getReconciliation();
        BigDecimal absolute = config.getToleranceAmount() == null ? BigDecimal.ZERO : config.getToleranceAmount();
        BigDecimal percentage = config.getTolerancePercentage() == null ? BigDecimal.ZERO : config.getTolerancePercentage();
        BigDecimal relative = calculator.round(
                calculator.round(expectedAmount).multiply(percentage).divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP));
        return calculator.round(absolute.add(relative));
    }
}
