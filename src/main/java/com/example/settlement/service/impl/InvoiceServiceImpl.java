package com.example.settlement.service.impl;

import com.example.settlement.audit.AuditActions;
import com.example.settlement.audit.AuditService;
import com.example.settlement.config.SettlementProperties;
import com.example.settlement.dto.common.PageResponse;
import com.example.settlement.dto.request.InvoiceGenerateRequest;
import com.example.settlement.dto.response.InvoiceResponse;
import com.example.settlement.dto.response.OutstandingResponse;
import com.example.settlement.entity.Invoice;
import com.example.settlement.entity.Trade;
import com.example.settlement.enums.InvoiceStatus;
import com.example.settlement.enums.InvoiceType;
import com.example.settlement.enums.TradeType;
import com.example.settlement.exception.BusinessRuleException;
import com.example.settlement.exception.CurrencyMismatchException;
import com.example.settlement.exception.DuplicateResourceException;
import com.example.settlement.exception.InvalidStateTransitionException;
import com.example.settlement.exception.ResourceNotFoundException;
import com.example.settlement.mapper.InvoiceMapper;
import com.example.settlement.repository.InvoiceRepository;
import com.example.settlement.repository.PaymentRepository;
import com.example.settlement.service.InvoiceService;
import com.example.settlement.service.TradeService;
import com.example.settlement.util.InvoiceCalculator;
import com.example.settlement.util.ReferenceGenerator;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class InvoiceServiceImpl implements InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceServiceImpl.class);
    private static final String ENTITY = "INVOICE";
    private static final int REFERENCE_ATTEMPTS = 5;

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final TradeService tradeService;
    private final InvoiceCalculator calculator;
    private final SettlementProperties properties;
    private final AuditService auditService;

    public InvoiceServiceImpl(InvoiceRepository invoiceRepository,
                              PaymentRepository paymentRepository,
                              TradeService tradeService,
                              InvoiceCalculator calculator,
                              SettlementProperties properties,
                              AuditService auditService) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.tradeService = tradeService;
        this.calculator = calculator;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public InvoiceResponse generateFromTrade(Long tradeId, InvoiceGenerateRequest request) {
        InvoiceGenerateRequest req = request == null ? emptyRequest() : request;
        Trade trade = tradeService.requireTrade(tradeId);

        if (trade.getStatus() == null || !trade.getStatus().isInvoiceEligible()) {
            throw new BusinessRuleException("TRADE_NOT_INVOICE_ELIGIBLE",
                    "Trade " + trade.getTradeReference() + " is in status " + trade.getStatus()
                            + "; only COMPLETED trades can be invoiced");
        }
        if (!properties.getInvoice().isAllowMultipleInvoicesPerTrade() && invoiceRepository.existsByTradeId(tradeId)) {
            throw new DuplicateResourceException(
                    "An invoice already exists for trade " + trade.getTradeReference()
                            + ". Enable settlement.invoice.allow-multiple-invoices-per-trade to permit this.");
        }

        LocalDate invoiceDate = req.invoiceDate() != null ? req.invoiceDate() : LocalDate.now();
        LocalDate dueDate = req.dueDate() != null
                ? req.dueDate()
                : invoiceDate.plusDays(properties.getInvoice().getDefaultPaymentTermDays());
        if (dueDate.isBefore(invoiceDate)) {
            throw new BusinessRuleException("INVALID_DUE_DATE",
                    "dueDate (" + dueDate + ") must not be earlier than invoiceDate (" + invoiceDate + ")");
        }

        String invoiceCurrency = StringUtils.hasText(req.currencyCode())
                ? req.currencyCode().trim().toUpperCase(Locale.ROOT)
                : trade.getCurrencyCode();
        if (!invoiceCurrency.equals(trade.getCurrencyCode())) {
            throw new CurrencyMismatchException(trade.getCurrencyCode(), invoiceCurrency);
        }

        BigDecimal taxPercentage = req.taxPercentage() != null
                ? req.taxPercentage()
                : properties.getInvoice().getDefaultTaxPercentage();
        InvoiceCalculator.Amounts amounts = calculator.calculate(
                trade.getQuantity(), trade.getUnitPrice(), taxPercentage,
                req.additionalCharges(), req.discountAmount());

        InvoiceType invoiceType = req.invoiceType() != null ? req.invoiceType() : deriveInvoiceType(trade.getTradeType());

        Invoice invoice = Invoice.builder()
                .invoiceNumber(generateUniqueInvoiceNumber())
                .trade(trade)
                .tradeReference(trade.getTradeReference())
                .invoiceType(invoiceType)
                .buyerName(trade.getBuyerName())
                .sellerName(trade.getSellerName())
                .invoiceDate(invoiceDate)
                .dueDate(dueDate)
                .commodityDescription(StringUtils.hasText(req.commodityDescription())
                        ? req.commodityDescription().trim()
                        : trade.getCommodity())
                .quantity(trade.getQuantity())
                .unitPrice(trade.getUnitPrice())
                .subtotal(amounts.subtotal())
                .taxPercentage(calculator.roundTaxPercentage(taxPercentage))
                .taxAmount(amounts.taxAmount())
                .additionalCharges(calculator.round(req.additionalCharges()))
                .discountAmount(calculator.round(req.discountAmount()))
                .totalAmount(amounts.totalAmount())
                .amountPaid(BigDecimal.ZERO.setScale(calculator.scale()))
                .currencyCode(invoiceCurrency)
                .status(InvoiceStatus.DRAFT)
                .notes(req.notes())
                .build();

        Invoice saved = invoiceRepository.save(invoice);
        InvoiceResponse response = InvoiceMapper.toResponse(saved);
        auditService.record(ENTITY, saved.getId(), AuditActions.INVOICE_GENERATED, null, response,
                "Invoice generated from trade " + trade.getTradeReference());
        log.info("Generated invoice {} for trade {}", saved.getInvoiceNumber(), trade.getTradeReference());
        return response;
    }

    @Override
    @Transactional
    public InvoiceResponse issue(Long id) {
        Invoice invoice = requireInvoice(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new InvalidStateTransitionException("invoice", invoice.getStatus(), InvoiceStatus.ISSUED);
        }
        InvoiceStatus before = invoice.getStatus();
        invoice.setStatus(InvoiceStatus.ISSUED);
        // A back-dated invoice whose due date already passed is overdue immediately.
        if (invoice.getDueDate().isBefore(LocalDate.now())) {
            invoice.setStatus(InvoiceStatus.OVERDUE);
        }
        Invoice saved = invoiceRepository.save(invoice);
        InvoiceResponse response = InvoiceMapper.toResponse(saved);
        auditService.record(ENTITY, saved.getId(), AuditActions.INVOICE_ISSUED, before, saved.getStatus(),
                "Invoice " + saved.getInvoiceNumber() + " issued");
        return response;
    }

    @Override
    @Transactional
    public InvoiceResponse cancel(Long id, String reason) {
        Invoice invoice = requireInvoice(id);
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new InvalidStateTransitionException("invoice", invoice.getStatus(), InvoiceStatus.CANCELLED);
        }
        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getAmountPaid().signum() > 0) {
            throw new BusinessRuleException("INVOICE_HAS_PAYMENTS",
                    "Invoice " + invoice.getInvoiceNumber()
                            + " cannot be cancelled because payments have already been recorded against it");
        }
        InvoiceStatus before = invoice.getStatus();
        invoice.setStatus(InvoiceStatus.CANCELLED);
        if (StringUtils.hasText(reason)) {
            invoice.setNotes(appendNote(invoice.getNotes(), reason));
        }
        Invoice saved = invoiceRepository.save(invoice);
        InvoiceResponse response = InvoiceMapper.toResponse(saved);
        auditService.record(ENTITY, saved.getId(), AuditActions.INVOICE_CANCELLED, before, saved.getStatus(),
                StringUtils.hasText(reason) ? reason : "Invoice cancelled");
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getById(Long id) {
        return InvoiceMapper.toResponse(requireInvoice(id));
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getByNumber(String invoiceNumber) {
        Invoice invoice = invoiceRepository.findByInvoiceNumber(invoiceNumber.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceNumber));
        return InvoiceMapper.toResponse(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> search(String invoiceNumber, String tradeReference, InvoiceStatus status,
                                                InvoiceType invoiceType, String currencyCode, Long tradeId,
                                                LocalDate invoiceDateFrom, LocalDate invoiceDateTo, Pageable pageable) {
        Specification<Invoice> specification = buildSpecification(
                invoiceNumber, tradeReference, status, invoiceType, currencyCode, tradeId, invoiceDateFrom, invoiceDateTo);
        Page<Invoice> page = invoiceRepository.findAll(specification, pageable);
        return PageResponse.from(page, InvoiceMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OutstandingResponse getOutstanding(Long id) {
        return InvoiceMapper.toOutstanding(requireInvoice(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceResponse> findOverdue() {
        return invoiceRepository.findOverdueCandidates(LocalDate.now()).stream()
                .map(InvoiceMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceResponse> findByTradeId(Long tradeId) {
        tradeService.requireTrade(tradeId);
        return invoiceRepository.findByTradeId(tradeId).stream()
                .map(InvoiceMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public String renderPrintable(Long id) {
        Invoice invoice = requireInvoice(id);
        String separator = "=".repeat(78);
        String thin = "-".repeat(78);
        StringBuilder sb = new StringBuilder();
        sb.append(separator).append(System.lineSeparator());
        sb.append(centre("INVOICE", 78)).append(System.lineSeparator());
        sb.append(separator).append(System.lineSeparator());
        appendRow(sb, "Invoice Number", invoice.getInvoiceNumber());
        appendRow(sb, "Trade Reference", invoice.getTradeReference());
        appendRow(sb, "Invoice Type", String.valueOf(invoice.getInvoiceType()));
        appendRow(sb, "Status", String.valueOf(invoice.getStatus()));
        appendRow(sb, "Invoice Date", String.valueOf(invoice.getInvoiceDate()));
        appendRow(sb, "Due Date", String.valueOf(invoice.getDueDate()));
        sb.append(thin).append(System.lineSeparator());
        appendRow(sb, "Buyer", invoice.getBuyerName());
        appendRow(sb, "Seller", invoice.getSellerName());
        sb.append(thin).append(System.lineSeparator());
        appendRow(sb, "Description", invoice.getCommodityDescription());
        appendRow(sb, "Quantity", money(invoice.getQuantity()));
        appendRow(sb, "Unit Price", money(invoice.getUnitPrice()) + " " + invoice.getCurrencyCode());
        sb.append(thin).append(System.lineSeparator());
        appendRow(sb, "Subtotal", money(invoice.getSubtotal()));
        appendRow(sb, "Tax (" + money(invoice.getTaxPercentage()) + "%)", money(invoice.getTaxAmount()));
        appendRow(sb, "Additional Charges", money(invoice.getAdditionalCharges()));
        appendRow(sb, "Discount", money(invoice.getDiscountAmount()));
        sb.append(thin).append(System.lineSeparator());
        appendRow(sb, "TOTAL", money(invoice.getTotalAmount()) + " " + invoice.getCurrencyCode());
        appendRow(sb, "Paid", money(invoice.getAmountPaid()));
        appendRow(sb, "Outstanding", money(invoice.outstandingAmount()));
        if (StringUtils.hasText(invoice.getNotes())) {
            sb.append(thin).append(System.lineSeparator());
            appendRow(sb, "Notes", invoice.getNotes());
        }
        sb.append(separator);
        return sb.toString();
    }

    @Override
    @Transactional(readOnly = true)
    public Invoice requireInvoice(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
    }

    @Override
    @Transactional
    public Invoice refreshSettlementState(Long invoiceId) {
        // Lock the invoice row first so concurrent payments are serialised and the
        // payment sum below can never be computed from a stale snapshot.
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
        BigDecimal netCompleted = paymentRepository.sumCompletedByInvoiceId(invoiceId);
        BigDecimal paid = calculator.round(netCompleted == null ? BigDecimal.ZERO : netCompleted);
        InvoiceStatus previousStatus = invoice.getStatus();

        invoice.setAmountPaid(paid);

        if (invoice.getStatus() != InvoiceStatus.CANCELLED) {
            boolean pastDue = invoice.getDueDate().isBefore(LocalDate.now());
            InvoiceStatus newStatus;
            if (paid.signum() > 0) {
                newStatus = paid.compareTo(invoice.getTotalAmount()) >= 0
                        ? InvoiceStatus.PAID
                        : (pastDue ? InvoiceStatus.OVERDUE : InvoiceStatus.PARTIALLY_PAID);
            } else if (invoice.getStatus() == InvoiceStatus.DRAFT) {
                newStatus = InvoiceStatus.DRAFT;
            } else {
                newStatus = pastDue ? InvoiceStatus.OVERDUE : InvoiceStatus.ISSUED;
            }
            invoice.setStatus(newStatus);
        }

        Invoice saved = invoiceRepository.save(invoice);
        if (previousStatus != saved.getStatus()) {
            auditService.record(ENTITY, saved.getId(), AuditActions.INVOICE_STATUS_CHANGED,
                    previousStatus, saved.getStatus(),
                    "Invoice status recomputed after settlement activity");
        }
        return saved;
    }

    @Override
    @Transactional
    public List<Invoice> refreshOverdueInvoices(LocalDate asOf) {
        List<Invoice> candidates = invoiceRepository.findOverdueCandidates(asOf);
        List<Invoice> updated = new ArrayList<>(candidates.size());
        for (Invoice invoice : candidates) {
            if (invoice.getStatus() != InvoiceStatus.OVERDUE) {
                InvoiceStatus before = invoice.getStatus();
                invoice.setStatus(InvoiceStatus.OVERDUE);
                Invoice saved = invoiceRepository.save(invoice);
                auditService.record(ENTITY, saved.getId(), AuditActions.INVOICE_STATUS_CHANGED,
                        before, InvoiceStatus.OVERDUE,
                        "Invoice marked OVERDUE by the monitoring job (due " + invoice.getDueDate() + ")");
                updated.add(saved);
            } else {
                updated.add(invoice);
            }
        }
        return updated;
    }

    private Specification<Invoice> buildSpecification(String invoiceNumber, String tradeReference,
                                                      InvoiceStatus status, InvoiceType invoiceType,
                                                      String currencyCode, Long tradeId,
                                                      LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(invoiceNumber)) {
                predicates.add(cb.like(cb.upper(root.get("invoiceNumber")),
                        "%" + invoiceNumber.trim().toUpperCase(Locale.ROOT) + "%"));
            }
            if (StringUtils.hasText(tradeReference)) {
                predicates.add(cb.like(cb.upper(root.get("tradeReference")),
                        "%" + tradeReference.trim().toUpperCase(Locale.ROOT) + "%"));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (invoiceType != null) {
                predicates.add(cb.equal(root.get("invoiceType"), invoiceType));
            }
            if (StringUtils.hasText(currencyCode)) {
                predicates.add(cb.equal(root.get("currencyCode"), currencyCode.trim().toUpperCase(Locale.ROOT)));
            }
            if (tradeId != null) {
                predicates.add(cb.equal(root.get("trade").get("id"), tradeId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("invoiceDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("invoiceDate"), to));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String generateUniqueInvoiceNumber() {
        for (int attempt = 0; attempt < REFERENCE_ATTEMPTS; attempt++) {
            String candidate = ReferenceGenerator.invoice();
            if (!invoiceRepository.existsByInvoiceNumber(candidate)) {
                return candidate;
            }
        }
        throw new BusinessRuleException("REFERENCE_GENERATION_FAILED",
                "Unable to generate a unique invoice number, please retry");
    }

    /** SELL trades produce a receivable invoice (customer owes us); BUY trades produce a payable invoice. */
    private InvoiceType deriveInvoiceType(TradeType tradeType) {
        return tradeType == TradeType.SELL ? InvoiceType.RECEIVABLE : InvoiceType.PAYABLE;
    }

    private InvoiceGenerateRequest emptyRequest() {
        return new InvoiceGenerateRequest(null, null, null, null, null, null, null, null, null);
    }

    private String appendNote(String existing, String addition) {
        if (!StringUtils.hasText(existing)) {
            return addition;
        }
        String combined = existing + " | " + addition;
        return combined.length() <= 500 ? combined : combined.substring(0, 497) + "...";
    }

    private void appendRow(StringBuilder sb, String label, String value) {
        sb.append(String.format("%-20s: %s%n", label, value == null ? "" : value));
    }

    private String centre(String text, int width) {
        int padding = Math.max(0, (width - text.length()) / 2);
        return " ".repeat(padding) + text;
    }

    private String money(BigDecimal value) {
        return calculator.round(value).toPlainString();
    }
}
