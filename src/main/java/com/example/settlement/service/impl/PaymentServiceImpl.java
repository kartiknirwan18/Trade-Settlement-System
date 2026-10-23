package com.example.settlement.service.impl;

import com.example.settlement.audit.AuditActions;
import com.example.settlement.audit.AuditService;
import com.example.settlement.config.SettlementProperties;
import com.example.settlement.dto.common.PageResponse;
import com.example.settlement.dto.request.PaymentCreateRequest;
import com.example.settlement.dto.response.PaymentResponse;
import com.example.settlement.entity.Invoice;
import com.example.settlement.entity.Payment;
import com.example.settlement.enums.PaymentDirection;
import com.example.settlement.enums.PaymentStatus;
import com.example.settlement.exception.BusinessRuleException;
import com.example.settlement.exception.CurrencyMismatchException;
import com.example.settlement.exception.InvalidStateTransitionException;
import com.example.settlement.exception.ResourceNotFoundException;
import com.example.settlement.mapper.PaymentMapper;
import com.example.settlement.repository.PaymentRepository;
import com.example.settlement.service.InvoiceService;
import com.example.settlement.service.PaymentService;
import com.example.settlement.util.ReferenceGenerator;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    private static final String ENTITY = "PAYMENT";
    private static final int REFERENCE_ATTEMPTS = 5;

    private final PaymentRepository paymentRepository;
    private final InvoiceService invoiceService;
    private final SettlementProperties properties;
    private final AuditService auditService;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              InvoiceService invoiceService,
                              SettlementProperties properties,
                              AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.invoiceService = invoiceService;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public PaymentResponse create(PaymentCreateRequest request) {
        Invoice invoice = invoiceService.requireInvoice(request.invoiceId());
        if (!invoice.getStatus().allowsPayment()) {
            throw new BusinessRuleException("INVOICE_NOT_PAYABLE",
                    "Invoice " + invoice.getInvoiceNumber() + " is " + invoice.getStatus()
                            + " and cannot accept new payments");
        }

        PaymentDirection expectedDirection = invoice.getInvoiceType().expectedPaymentDirection();
        PaymentDirection direction = request.direction() != null ? request.direction() : expectedDirection;
        if (direction != expectedDirection) {
            throw new BusinessRuleException("PAYMENT_DIRECTION_MISMATCH",
                    "Invoice " + invoice.getInvoiceNumber() + " is " + invoice.getInvoiceType()
                            + " and requires a " + expectedDirection + " payment, but " + direction + " was supplied");
        }

        String currency = StringUtils.hasText(request.currencyCode())
                ? request.currencyCode().trim().toUpperCase(Locale.ROOT)
                : invoice.getCurrencyCode();
        if (!currency.equals(invoice.getCurrencyCode())) {
            throw new CurrencyMismatchException(invoice.getCurrencyCode(), currency);
        }

        BigDecimal amount = requirePositiveAmount(request.amount());

        PaymentStatus status = request.status() != null ? request.status() : PaymentStatus.PENDING;
        if (status == PaymentStatus.REVERSED) {
            throw new BusinessRuleException("INVALID_PAYMENT_STATUS",
                    "A payment cannot be created in REVERSED status; create it and then reverse it");
        }
        if (status == PaymentStatus.COMPLETED) {
            validateNoOverpayment(invoice, amount);
        }

        String reference = StringUtils.hasText(request.paymentReference())
                ? request.paymentReference().trim().toUpperCase(Locale.ROOT)
                : generateUniqueReference();
        if (paymentRepository.existsByPaymentReference(reference)) {
            throw com.example.settlement.exception.DuplicateResourceException.of("paymentReference", reference);
        }

        Payment payment = Payment.builder()
                .paymentReference(reference)
                .invoice(invoice)
                .direction(direction)
                .paymentDate(request.paymentDate())
                .amount(amount)
                .currencyCode(currency)
                .paymentMethod(request.paymentMethod())
                .transactionReference(request.transactionReference())
                .status(status)
                .notes(request.notes())
                .build();

        Payment saved = paymentRepository.save(payment);

        if (status == PaymentStatus.COMPLETED) {
            invoiceService.refreshSettlementState(invoice.getId());
        }

        PaymentResponse response = PaymentMapper.toResponse(saved);
        auditService.record(ENTITY, saved.getId(), AuditActions.PAYMENT_CREATED, null, response,
                "Payment " + saved.getPaymentReference() + " recorded against invoice " + invoice.getInvoiceNumber());
        log.info("Recorded payment {} ({}) against invoice {}", saved.getPaymentReference(), status, invoice.getInvoiceNumber());
        return response;
    }

    @Override
    @Transactional
    public PaymentResponse complete(Long id) {
        Payment payment = requirePayment(id);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidStateTransitionException("payment", payment.getStatus(), PaymentStatus.COMPLETED);
        }
        Invoice invoice = invoiceService.requireInvoice(payment.getInvoice().getId());
        validateNoOverpayment(invoice, payment.getAmount());

        PaymentStatus before = payment.getStatus();
        payment.setStatus(PaymentStatus.COMPLETED);
        Payment saved = paymentRepository.save(payment);

        invoiceService.refreshSettlementState(invoice.getId());

        PaymentResponse response = PaymentMapper.toResponse(saved);
        auditService.record(ENTITY, saved.getId(), AuditActions.PAYMENT_COMPLETED, before, PaymentStatus.COMPLETED,
                "Payment " + saved.getPaymentReference() + " completed");
        log.info("Completed payment {}", saved.getPaymentReference());
        return response;
    }

    @Override
    @Transactional
    public PaymentResponse fail(Long id, String reason) {
        Payment payment = requirePayment(id);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidStateTransitionException("payment", payment.getStatus(), PaymentStatus.FAILED);
        }
        PaymentStatus before = payment.getStatus();
        payment.setStatus(PaymentStatus.FAILED);
        payment.setReversalReason(reason);
        Payment saved = paymentRepository.save(payment);
        PaymentResponse response = PaymentMapper.toResponse(saved);
        auditService.record(ENTITY, saved.getId(), AuditActions.PAYMENT_FAILED, before, PaymentStatus.FAILED,
                StringUtils.hasText(reason) ? reason : "Payment marked failed");
        return response;
    }

    @Override
    @Transactional
    public PaymentResponse reverse(Long id, String reason) {
        Payment payment = requirePayment(id);
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new InvalidStateTransitionException("payment", payment.getStatus(), PaymentStatus.REVERSED);
        }
        PaymentStatus before = payment.getStatus();
        payment.setStatus(PaymentStatus.REVERSED);
        payment.setReversalReason(reason);
        payment.setReversedAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);

        // The reversed payment no longer counts towards the settled amount, but the
        // original row is preserved so the transaction history stays auditable.
        Invoice settled = invoiceService.refreshSettlementState(saved.getInvoice().getId());

        PaymentResponse response = PaymentMapper.toResponse(saved);
        auditService.record(ENTITY, saved.getId(), AuditActions.PAYMENT_REVERSED, before, PaymentStatus.REVERSED,
                "Payment " + saved.getPaymentReference() + " reversed. Reason: "
                        + (StringUtils.hasText(reason) ? reason : "not supplied")
                        + ". Invoice " + settled.getInvoiceNumber() + " outstanding is now "
                        + settled.outstandingAmount().toPlainString());
        log.warn("Reversed payment {} against invoice {}", saved.getPaymentReference(), settled.getInvoiceNumber());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getById(Long id) {
        return PaymentMapper.toResponse(requirePayment(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> findByInvoiceId(Long invoiceId) {
        invoiceService.requireInvoice(invoiceId);
        return paymentRepository.findByInvoiceIdOrderByPaymentDateDescIdDesc(invoiceId).stream()
                .map(PaymentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> search(Long invoiceId, String paymentReference, PaymentStatus status,
                                                PaymentDirection direction, String currencyCode, String counterparty,
                                                LocalDate from, LocalDate to, Pageable pageable) {
        Specification<Payment> specification = buildSpecification(
                invoiceId, paymentReference, status, direction, currencyCode, counterparty, from, to);
        Page<Payment> page = paymentRepository.findAll(specification, pageable);
        return PageResponse.from(page, PaymentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Payment requirePayment(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
    }

    private void validateNoOverpayment(Invoice invoice, BigDecimal amount) {
        if (properties.getPayment().isAllowOverpayment()) {
            return;
        }
        BigDecimal outstanding = invoice.outstandingAmount();
        if (amount.compareTo(outstanding) > 0) {
            throw new BusinessRuleException("OVERPAYMENT_NOT_ALLOWED",
                    "Payment amount " + amount.toPlainString() + " exceeds the outstanding balance "
                            + outstanding.toPlainString() + " of invoice " + invoice.getInvoiceNumber()
                            + ". Set settlement.payment.allow-overpayment=true to permit overpayments.");
        }
    }

    private Specification<Payment> buildSpecification(Long invoiceId, String paymentReference, PaymentStatus status,
                                                      PaymentDirection direction, String currencyCode,
                                                      String counterparty, LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            boolean needsInvoiceJoin = invoiceId != null || StringUtils.hasText(counterparty);
            Join<Object, Object> invoice = null;
            if (needsInvoiceJoin) {
                invoice = root.join("invoice", JoinType.INNER);
            }
            if (invoiceId != null) {
                predicates.add(cb.equal(invoice.get("id"), invoiceId));
            }
            if (StringUtils.hasText(counterparty)) {
                String pattern = "%" + counterparty.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(invoice.get("buyerName")), pattern),
                        cb.like(cb.lower(invoice.get("sellerName")), pattern)));
            }
            if (StringUtils.hasText(paymentReference)) {
                predicates.add(cb.like(cb.upper(root.get("paymentReference")),
                        "%" + paymentReference.trim().toUpperCase(Locale.ROOT) + "%"));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (direction != null) {
                predicates.add(cb.equal(root.get("direction"), direction));
            }
            if (StringUtils.hasText(currencyCode)) {
                predicates.add(cb.equal(root.get("currencyCode"), currencyCode.trim().toUpperCase(Locale.ROOT)));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("paymentDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("paymentDate"), to));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String generateUniqueReference() {
        for (int attempt = 0; attempt < REFERENCE_ATTEMPTS; attempt++) {
            String candidate = ReferenceGenerator.payment();
            if (!paymentRepository.existsByPaymentReference(candidate)) {
                return candidate;
            }
        }
        throw new BusinessRuleException("REFERENCE_GENERATION_FAILED",
                "Unable to generate a unique payment reference, please retry");
    }

    private BigDecimal requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("INVALID_AMOUNT", "amount must be greater than zero");
        }
        return amount;
    }
}
