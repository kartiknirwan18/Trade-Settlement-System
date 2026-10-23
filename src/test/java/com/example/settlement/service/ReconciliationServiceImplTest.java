package com.example.settlement.service;

import com.example.settlement.audit.AuditActions;
import com.example.settlement.audit.AuditService;
import com.example.settlement.config.SettlementProperties;
import com.example.settlement.dto.request.ReconciliationResolveRequest;
import com.example.settlement.dto.response.ReconciliationResponse;
import com.example.settlement.entity.Invoice;
import com.example.settlement.entity.Reconciliation;
import com.example.settlement.entity.Trade;
import com.example.settlement.enums.InvoiceStatus;
import com.example.settlement.enums.InvoiceType;
import com.example.settlement.enums.ReconciliationStatus;
import com.example.settlement.enums.TradeStatus;
import com.example.settlement.enums.TradeType;
import com.example.settlement.exception.BusinessRuleException;
import com.example.settlement.repository.PaymentRepository;
import com.example.settlement.repository.ReconciliationRepository;
import com.example.settlement.security.CurrentUserProvider;
import com.example.settlement.service.impl.ReconciliationServiceImpl;
import com.example.settlement.util.InvoiceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for invoice reconciliation, variance calculation and tolerance handling. */
@ExtendWith(MockitoExtension.class)
class ReconciliationServiceImplTest {

    private static final BigDecimal EXPECTED = new BigDecimal("1000.00");

    @Mock
    private ReconciliationRepository reconciliationRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private AuditService auditService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private SettlementProperties properties;
    private ReconciliationServiceImpl reconciliationService;

    @BeforeEach
    void setUp() {
        properties = new SettlementProperties();
        reconciliationService = new ReconciliationServiceImpl(reconciliationRepository, paymentRepository,
                invoiceService, new InvoiceCalculator(properties), properties, auditService, currentUserProvider);
    }

    private static Invoice invoice(String currency) {
        return Invoice.builder()
                .id(21L)
                .invoiceNumber("INV-2026-000123")
                .trade(Trade.builder().id(11L).tradeReference("TRD-1").tradeType(TradeType.SELL)
                        .status(TradeStatus.COMPLETED).build())
                .tradeReference("TRD-1")
                .invoiceType(InvoiceType.RECEIVABLE)
                .buyerName("Shell Trading Ltd")
                .sellerName("BP Energy")
                .invoiceDate(LocalDate.now().minusDays(30))
                .dueDate(LocalDate.now().plusDays(5))
                .commodityDescription("Brent Crude Oil")
                .quantity(new BigDecimal("10.0000"))
                .unitPrice(new BigDecimal("100.0000"))
                .subtotal(EXPECTED)
                .taxPercentage(BigDecimal.ZERO.setScale(2))
                .taxAmount(BigDecimal.ZERO.setScale(2))
                .additionalCharges(BigDecimal.ZERO.setScale(2))
                .discountAmount(BigDecimal.ZERO.setScale(2))
                .totalAmount(EXPECTED)
                .amountPaid(BigDecimal.ZERO.setScale(2))
                .currencyCode(currency)
                .status(InvoiceStatus.ISSUED)
                .build();
    }

    private void stubRun(String currency, BigDecimal settled, long foreignCurrencyPayments) {
        when(invoiceService.requireInvoice(21L)).thenReturn(invoice(currency));
        when(paymentRepository.sumCompletedByInvoiceIdAndCurrency(21L, currency)).thenReturn(settled);
        when(paymentRepository.countCompletedInOtherCurrency(21L, currency)).thenReturn(foreignCurrencyPayments);
        when(reconciliationRepository.save(any(Reconciliation.class))).thenAnswer(invocation -> {
            Reconciliation toSave = invocation.getArgument(0);
            if (toSave.getId() == null) {
                toSave.setId(41L);
            }
            return toSave;
        });
    }

    @Test
    @DisplayName("an exactly settled invoice is MATCHED with zero variance")
    void matchedWhenFullySettled() {
        stubRun("USD", new BigDecimal("1000.00"), 0);

        ReconciliationResponse response = reconciliationService.runForInvoice(21L);

        assertThat(response.status()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(response.expectedAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.actualSettledAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.varianceAmount()).isEqualByComparingTo("0.00");
        assertThat(response.resolved()).isTrue();
        verify(auditService).record(eq("RECONCILIATION"), eq(41L), eq(AuditActions.RECONCILIATION_RUN), isNull(), any(), anyString());
    }

    @Test
    @DisplayName("a variance inside the configured tolerance is still MATCHED")
    void matchedWithinTolerance() {
        // default absolute tolerance is 0.01
        stubRun("USD", new BigDecimal("999.99"), 0);

        ReconciliationResponse response = reconciliationService.runForInvoice(21L);

        assertThat(response.status()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(response.varianceAmount()).isEqualByComparingTo("-0.01");
        assertThat(response.toleranceApplied()).isEqualByComparingTo("0.01");
    }

    @Test
    @DisplayName("a shortfall beyond the tolerance is UNDERPAID with a negative variance")
    void underpaidWhenSettledBelowExpected() {
        stubRun("USD", new BigDecimal("850.00"), 0);

        ReconciliationResponse response = reconciliationService.runForInvoice(21L);

        assertThat(response.status()).isEqualTo(ReconciliationStatus.UNDERPAID);
        assertThat(response.varianceAmount()).isEqualByComparingTo("-150.00");
        assertThat(response.resolved()).isFalse();
    }

    @Test
    @DisplayName("an excess above the tolerance is OVERPAID with a positive variance")
    void overpaidWhenSettledAboveExpected() {
        stubRun("USD", new BigDecimal("1200.00"), 0);

        ReconciliationResponse response = reconciliationService.runForInvoice(21L);

        assertThat(response.status()).isEqualTo(ReconciliationStatus.OVERPAID);
        assertThat(response.varianceAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("an invoice with no completed payments is UNMATCHED")
    void unmatchedWhenNoPayments() {
        stubRun("USD", BigDecimal.ZERO, 0);

        ReconciliationResponse response = reconciliationService.runForInvoice(21L);

        assertThat(response.status()).isEqualTo(ReconciliationStatus.UNMATCHED);
        assertThat(response.varianceAmount()).isEqualByComparingTo("-1000.00");
    }

    @Test
    @DisplayName("settlement in another currency forces REVIEW_REQUIRED instead of a bogus match")
    void reviewRequiredWhenForeignCurrencyPaymentsExist() {
        stubRun("USD", new BigDecimal("400.00"), 1);

        ReconciliationResponse response = reconciliationService.runForInvoice(21L);

        assertThat(response.status()).isEqualTo(ReconciliationStatus.REVIEW_REQUIRED);
        assertThat(response.notes()).contains("currency");
    }

    @Test
    @DisplayName("the relative tolerance widens the matching band")
    void relativeToleranceWidensTheBand() {
        properties.getReconciliation().setTolerancePercentage(new BigDecimal("2.00"));
        // 2% of 1000 = 20, plus the 0.01 absolute tolerance => 20.01
        stubRun("USD", new BigDecimal("985.00"), 0);

        ReconciliationResponse response = reconciliationService.runForInvoice(21L);

        assertThat(response.toleranceApplied()).isEqualByComparingTo("20.01");
        assertThat(response.status()).isEqualTo(ReconciliationStatus.MATCHED);
    }

    @Test
    @DisplayName("resolving a discrepancy records the user, notes and audit entry")
    void resolveRecordsUserAndAudit() {
        Reconciliation discrepancy = Reconciliation.builder()
                .id(41L)
                .invoice(invoice("USD"))
                .expectedAmount(EXPECTED)
                .actualSettledAmount(new BigDecimal("850.00"))
                .varianceAmount(new BigDecimal("-150.00"))
                .currencyCode("USD")
                .reconciliationDate(LocalDate.now())
                .status(ReconciliationStatus.UNDERPAID)
                .resolved(false)
                .build();
        when(reconciliationRepository.findById(41L)).thenReturn(Optional.of(discrepancy));
        when(currentUserProvider.currentUsername()).thenReturn("finance.user");
        when(reconciliationRepository.save(any(Reconciliation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReconciliationResponse response = reconciliationService.resolve(41L,
                new ReconciliationResolveRequest("Short payment written off as bank charges", ReconciliationStatus.MATCHED));

        assertThat(response.resolved()).isTrue();
        assertThat(response.resolvedBy()).isEqualTo("finance.user");
        assertThat(response.status()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(response.resolvedAt()).isNotNull();
        verify(auditService).record(eq("RECONCILIATION"), eq(41L), eq(AuditActions.RECONCILIATION_RESOLVED),
                eq("UNDERPAID"), eq("MATCHED"), anyString());
    }

    @Test
    @DisplayName("an already resolved discrepancy cannot be resolved twice")
    void resolveRejectsAlreadyResolved() {
        Reconciliation resolved = Reconciliation.builder()
                .id(41L)
                .invoice(invoice("USD"))
                .expectedAmount(EXPECTED)
                .actualSettledAmount(EXPECTED)
                .varianceAmount(BigDecimal.ZERO.setScale(2))
                .currencyCode("USD")
                .reconciliationDate(LocalDate.now())
                .status(ReconciliationStatus.MATCHED)
                .resolved(true)
                .resolvedBy("finance.user")
                .build();
        when(reconciliationRepository.findById(41L)).thenReturn(Optional.of(resolved));

        assertThatThrownBy(() -> reconciliationService.resolve(41L,
                new ReconciliationResolveRequest("Again", null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo("RECONCILIATION_ALREADY_RESOLVED");
        verify(reconciliationRepository, never()).save(any());
    }
}
