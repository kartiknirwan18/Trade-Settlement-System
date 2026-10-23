package com.example.settlement.service.impl;

import com.example.settlement.audit.AuditActions;
import com.example.settlement.audit.AuditService;
import com.example.settlement.config.SettlementProperties;
import com.example.settlement.dto.common.PageResponse;
import com.example.settlement.dto.response.AlertResponse;
import com.example.settlement.entity.Invoice;
import com.example.settlement.entity.OverdueAlert;
import com.example.settlement.enums.AlertSeverity;
import com.example.settlement.enums.AlertType;
import com.example.settlement.exception.InvalidStateTransitionException;
import com.example.settlement.exception.ResourceNotFoundException;
import com.example.settlement.mapper.AlertMapper;
import com.example.settlement.repository.InvoiceRepository;
import com.example.settlement.repository.OverdueAlertRepository;
import com.example.settlement.security.CurrentUserProvider;
import com.example.settlement.service.AlertService;
import com.example.settlement.service.InvoiceService;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AlertServiceImpl implements AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertServiceImpl.class);
    private static final String ENTITY = "ALERT";

    private final OverdueAlertRepository alertRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final SettlementProperties properties;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

    public AlertServiceImpl(OverdueAlertRepository alertRepository,
                            InvoiceRepository invoiceRepository,
                            InvoiceService invoiceService,
                            SettlementProperties properties,
                            AuditService auditService,
                            CurrentUserProvider currentUserProvider) {
        this.alertRepository = alertRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
        this.properties = properties;
        this.auditService = auditService;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public List<AlertResponse> scanAndRaiseAlerts(LocalDate asOf) {
        LocalDate effectiveDate = asOf == null ? LocalDate.now() : asOf;
        List<OverdueAlert> raised = new ArrayList<>();

        // 1. Move every invoice whose due date has passed into OVERDUE first, so the
        //    alert message and severity reflect the refreshed state.
        for (Invoice invoice : invoiceService.refreshOverdueInvoices(effectiveDate)) {
            long daysPastDue = ChronoUnit.DAYS.between(invoice.getDueDate(), effectiveDate);
            String message = "Invoice " + invoice.getInvoiceNumber() + " is " + daysPastDue
                    + " day(s) past due; outstanding " + invoice.outstandingAmount().toPlainString()
                    + " " + invoice.getCurrencyCode();
            OverdueAlert alert = raiseIfAbsent(invoice, AlertType.OVERDUE_INVOICE,
                    overdueSeverity(daysPastDue), message, effectiveDate);
            if (alert != null) {
                raised.add(alert);
            }
        }

        // 2. Warn about invoices that are about to fall due so finance can act early.
        int window = Math.max(0, properties.getOverdue().getUpcomingDueDays());
        if (window > 0) {
            LocalDate windowEnd = effectiveDate.plusDays(window);
            for (Invoice invoice : invoiceRepository.findDueBetween(effectiveDate, windowEnd)) {
                long daysUntilDue = ChronoUnit.DAYS.between(effectiveDate, invoice.getDueDate());
                String message = "Invoice " + invoice.getInvoiceNumber() + " is due on " + invoice.getDueDate()
                        + " (" + daysUntilDue + " day(s)); outstanding " + invoice.outstandingAmount().toPlainString()
                        + " " + invoice.getCurrencyCode();
                OverdueAlert alert = raiseIfAbsent(invoice, AlertType.UPCOMING_DUE,
                        daysUntilDue <= 1 ? AlertSeverity.MEDIUM : AlertSeverity.LOW, message, effectiveDate);
                if (alert != null) {
                    raised.add(alert);
                }
            }
        }

        log.info("Alert scan for {} completed: {} new alert(s) raised", effectiveDate, raised.size());
        return raised.stream().map(AlertMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AlertResponse> search(Boolean acknowledged, AlertSeverity severity, AlertType alertType,
                                              Long invoiceId, String invoiceNumber, String currencyCode,
                                              LocalDate triggerDateFrom, LocalDate triggerDateTo, Pageable pageable) {
        Specification<OverdueAlert> specification = buildSpecification(acknowledged, severity, alertType, invoiceId,
                invoiceNumber, currencyCode, triggerDateFrom, triggerDateTo);
        Page<OverdueAlert> page = alertRepository.findAll(specification, pageable);
        return PageResponse.from(page, AlertMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertResponse> findActiveOverdue() {
        return alertRepository.findByAcknowledgedFalseOrderBySeverityAscTriggerDateAsc().stream()
                .filter(alert -> alert.getAlertType() == AlertType.OVERDUE_INVOICE)
                .map(AlertMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public AlertResponse acknowledge(Long id) {
        OverdueAlert alert = requireAlert(id);
        if (alert.isAcknowledged()) {
            throw new InvalidStateTransitionException("alert", "ACKNOWLEDGED", "ACKNOWLEDGED");
        }
        alert.setAcknowledged(true);
        alert.setAcknowledgedBy(currentUserProvider.currentUsername());
        alert.setAcknowledgedAt(LocalDateTime.now());
        OverdueAlert saved = alertRepository.save(alert);
        AlertResponse response = AlertMapper.toResponse(saved);
        auditService.record(ENTITY, saved.getId(), AuditActions.ALERT_ACKNOWLEDGED, response,
                "Alert " + saved.getAlertType() + " acknowledged");
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public OverdueAlert requireAlert(Long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OverdueAlert", id));
    }

    /**
     * Creates the alert only when no alert of the same type exists for the invoice.
     * The unique constraint on (invoice_id, alert_type) is the database-level backstop.
     */
    private OverdueAlert raiseIfAbsent(Invoice invoice, AlertType alertType, AlertSeverity severity,
                                       String message, LocalDate triggerDate) {
        if (alertRepository.existsByInvoiceIdAndAlertType(invoice.getId(), alertType)) {
            return null;
        }
        OverdueAlert alert = OverdueAlert.builder()
                .invoice(invoice)
                .alertType(alertType)
                .message(message)
                .triggerDate(triggerDate)
                .severity(severity)
                .acknowledged(false)
                .build();
        OverdueAlert saved = alertRepository.save(alert);
        auditService.record(ENTITY, saved.getId(), AuditActions.ALERT_CREATED, AlertMapper.toResponse(saved),
                "Alert " + alertType + " raised for invoice " + invoice.getInvoiceNumber());
        return saved;
    }

    /** Escalation keyed on how far past due the invoice is. */
    private AlertSeverity overdueSeverity(long daysPastDue) {
        if (daysPastDue > properties.getOverdue().getHighSeverityAfterDays()) {
            return AlertSeverity.CRITICAL;
        }
        if (daysPastDue > 7) {
            return AlertSeverity.HIGH;
        }
        if (daysPastDue > 1) {
            return AlertSeverity.MEDIUM;
        }
        return AlertSeverity.LOW;
    }

    private Specification<OverdueAlert> buildSpecification(Boolean acknowledged, AlertSeverity severity,
                                                           AlertType alertType, Long invoiceId, String invoiceNumber,
                                                           String currencyCode, LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (acknowledged != null) {
                predicates.add(cb.equal(root.get("acknowledged"), acknowledged));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (alertType != null) {
                predicates.add(cb.equal(root.get("alertType"), alertType));
            }
            if (invoiceId != null) {
                predicates.add(cb.equal(root.get("invoice").get("id"), invoiceId));
            }
            if (StringUtils.hasText(invoiceNumber)) {
                predicates.add(cb.like(cb.upper(root.get("invoice").get("invoiceNumber")),
                        "%" + invoiceNumber.trim().toUpperCase(Locale.ROOT) + "%"));
            }
            if (StringUtils.hasText(currencyCode)) {
                predicates.add(cb.equal(root.get("invoice").get("currencyCode"),
                        currencyCode.trim().toUpperCase(Locale.ROOT)));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("triggerDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("triggerDate"), to));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
