package com.example.settlement.service;

import com.example.settlement.audit.AuditActions;
import com.example.settlement.audit.AuditService;
import com.example.settlement.config.SettlementProperties;
import com.example.settlement.dto.request.InvoiceGenerateRequest;
import com.example.settlement.dto.response.InvoiceResponse;
import com.example.settlement.entity.Invoice;
import com.example.settlement.entity.Trade;
import com.example.settlement.enums.InvoiceStatus;
import com.example.settlement.enums.InvoiceType;
import com.example.settlement.enums.TradeStatus;
import com.example.settlement.enums.TradeType;
import com.example.settlement.exception.BusinessRuleException;
import com.example.settlement.exception.CurrencyMismatchException;
import com.example.settlement.exception.DuplicateResourceException;
import com.example.settlement.exception.InvalidStateTransitionException;
import com.example.settlement.repository.InvoiceRepository;
import com.example.settlement.repository.PaymentRepository;
import com.example.settlement.service.impl.InvoiceServiceImpl;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for invoice generation, issue/cancel transitions and settlement recalculation. */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TradeService tradeService;

    @Mock
    private AuditService auditService;

    private SettlementProperties properties;
    private InvoiceServiceImpl invoiceService;

    @BeforeEach
    void setUp() {
        properties = new SettlementProperties();
        invoiceService = new InvoiceServiceImpl(invoiceRepository, paymentRepository, tradeService,
                new InvoiceCalculator(properties), properties, auditService);
    }

    private static Trade trade(TradeStatus status, String currency) {
        return Trade.builder()
                .id(11L)
                .tradeReference("TRD-20260115-8KQ2ZP")
                .commodity("Brent Crude Oil")
                .tradeType(TradeType.SELL)
                .buyerName("Shell Trading Ltd")
                .sellerName("BP Energy")
                .quantity(new BigDecimal("50000.0000"))
                .unitOfMeasure("BARREL")
                .unitPrice(new BigDecimal("82.4500"))
                .currencyCode(currency)
                .tradeDate(LocalDate.of(2026, 1, 15))
                .deliveryDate(LocalDate.of(2026, 2, 1))
                .status(status)
                .build();
    }

    private static Invoice invoice(InvoiceStatus status, BigDecimal totalAmount, BigDecimal amountPaid, String currency) {
        return Invoice.builder()
                .id(21L)
                .invoiceNumber("INV-2026-000123")
                .trade(trade(TradeStatus.COMPLETED, currency))
                .tradeReference("TRD-20260115-8KQ2ZP")
                .invoiceType(InvoiceType.RECEIVABLE)
                .buyerName("Shell Trading Ltd")
                .sellerName("BP Energy")
                .invoiceDate(LocalDate.now().minusDays(45))
                .dueDate(LocalDate.now().minusDays(15))
                .commodityDescription("Brent Crude Oil")
                .quantity(new BigDecimal("50000.0000"))
                .unitPrice(new BigDecimal("82.4500"))
                .subtotal(new BigDecimal("4122500.00"))
                .taxPercentage(new BigDecimal("5.00"))
                .taxAmount(new BigDecimal("206125.00"))
                .additionalCharges(BigDecimal.ZERO.setScale(2))
                .discountAmount(BigDecimal.ZERO.setScale(2))
                .totalAmount(totalAmount)
                .amountPaid(amountPaid)
                .currencyCode(currency)
                .status(status)
                .build();
    }

    private void stubSaveAssigningId() {
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice toSave = invocation.getArgument(0);
            if (toSave.getId() == null) {
                toSave.setId(21L);
            }
            return toSave;
        });
    }

    @Test
    @DisplayName("an invoice can only be generated from a COMPLETED trade")
    void rejectsNonCompletedTrade() {
        when(tradeService.requireTrade(11L)).thenReturn(trade(TradeStatus.CONFIRMED, "USD"));

        assertThatThrownBy(() -> invoiceService.generateFromTrade(11L, null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo("TRADE_NOT_INVOICE_ELIGIBLE");
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("a second invoice for the same trade is rejected by default")
    void rejectsDuplicateInvoiceForTrade() {
        when(tradeService.requireTrade(11L)).thenReturn(trade(TradeStatus.COMPLETED, "USD"));
        when(invoiceRepository.existsByTradeId(11L)).thenReturn(true);

        assertThatThrownBy(() -> invoiceService.generateFromTrade(11L, null))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("an invoice currency that differs from the trade currency is rejected")
    void rejectsCurrencyMismatch() {
        when(tradeService.requireTrade(11L)).thenReturn(trade(TradeStatus.COMPLETED, "USD"));
        when(invoiceRepository.existsByTradeId(11L)).thenReturn(false);

        InvoiceGenerateRequest request = new InvoiceGenerateRequest(
                null, null, null, null, null, null, null, "EUR", null);

        assertThatThrownBy(() -> invoiceService.generateFromTrade(11L, request))
                .isInstanceOf(CurrencyMismatchException.class);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("a due date earlier than the invoice date is rejected")
    void rejectsDueDateBeforeInvoiceDate() {
        when(tradeService.requireTrade(11L)).thenReturn(trade(TradeStatus.COMPLETED, "USD"));
        when(invoiceRepository.existsByTradeId(11L)).thenReturn(false);

        InvoiceGenerateRequest request = new InvoiceGenerateRequest(
                null, null, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 1), null, null, null, null, null);

        assertThatThrownBy(() -> invoiceService.generateFromTrade(11L, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo("INVALID_DUE_DATE");
    }

    @Test
    @DisplayName("generation snapshots the trade and computes subtotal, tax and total")
    void generatesDraftWithCalculatedAmounts() {
        when(tradeService.requireTrade(11L)).thenReturn(trade(TradeStatus.COMPLETED, "USD"));
        when(invoiceRepository.existsByTradeId(11L)).thenReturn(false);
        when(invoiceRepository.existsByInvoiceNumber(anyString())).thenReturn(false);
        stubSaveAssigningId();

        InvoiceGenerateRequest request = new InvoiceGenerateRequest(
                null, null, LocalDate.of(2026, 1, 20), null,
                new BigDecimal("5.00"), new BigDecimal("1500.00"), new BigDecimal("250.00"), null, "First cargo");

        InvoiceResponse response = invoiceService.generateFromTrade(11L, request);

        assertThat(response.status()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(response.invoiceNumber()).startsWith("INV-");
        assertThat(response.tradeReference()).isEqualTo("TRD-20260115-8KQ2ZP");
        assertThat(response.subtotal()).isEqualByComparingTo("4122500.00");
        assertThat(response.taxAmount()).isEqualByComparingTo("206125.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("4329875.00");
        assertThat(response.amountPaid()).isEqualByComparingTo("0.00");
        assertThat(response.outstandingAmount()).isEqualByComparingTo("4329875.00");
        // default payment term is 30 days
        assertThat(response.dueDate()).isEqualTo(LocalDate.of(2026, 2, 19));
        verify(auditService).record(eq("INVOICE"), eq(21L), eq(AuditActions.INVOICE_GENERATED), isNull(), any(), anyString());
    }

    @Test
    @DisplayName("a SELL trade produces a receivable invoice and a BUY trade a payable one")
    void derivesInvoiceTypeFromTradeType() {
        Trade buyTrade = trade(TradeStatus.COMPLETED, "USD");
        buyTrade.setTradeType(TradeType.BUY);
        when(tradeService.requireTrade(11L)).thenReturn(buyTrade);
        when(invoiceRepository.existsByTradeId(11L)).thenReturn(false);
        when(invoiceRepository.existsByInvoiceNumber(anyString())).thenReturn(false);
        stubSaveAssigningId();

        InvoiceResponse response = invoiceService.generateFromTrade(11L, null);

        assertThat(response.invoiceType()).isEqualTo(InvoiceType.PAYABLE);
        assertThat(response.taxPercentage()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("issuing a draft invoice moves it to ISSUED")
    void issueMovesDraftToIssued() {
        Invoice draft = invoice(InvoiceStatus.DRAFT, new BigDecimal("4329875.00"), BigDecimal.ZERO.setScale(2), "USD");
        draft.setDueDate(LocalDate.now().plusDays(10));
        when(invoiceRepository.findById(21L)).thenReturn(Optional.of(draft));
        stubSaveAssigningId();

        InvoiceResponse response = invoiceService.issue(21L);

        assertThat(response.status()).isEqualTo(InvoiceStatus.ISSUED);
        verify(auditService).record(eq("INVOICE"), eq(21L), eq(AuditActions.INVOICE_ISSUED), eq(InvoiceStatus.DRAFT), eq(InvoiceStatus.ISSUED), anyString());
    }

    @Test
    @DisplayName("issuing a back-dated invoice whose due date has passed marks it OVERDUE")
    void issueMarksBackdatedInvoiceOverdue() {
        Invoice draft = invoice(InvoiceStatus.DRAFT, new BigDecimal("4329875.00"), BigDecimal.ZERO.setScale(2), "USD");
        when(invoiceRepository.findById(21L)).thenReturn(Optional.of(draft));
        stubSaveAssigningId();

        InvoiceResponse response = invoiceService.issue(21L);

        assertThat(response.status()).isEqualTo(InvoiceStatus.OVERDUE);
        assertThat(response.overdue()).isTrue();
    }

    @Test
    @DisplayName("a non-draft invoice cannot be issued again")
    void issueRejectsNonDraftInvoice() {
        Invoice issued = invoice(InvoiceStatus.ISSUED, new BigDecimal("4329875.00"), BigDecimal.ZERO.setScale(2), "USD");
        when(invoiceRepository.findById(21L)).thenReturn(Optional.of(issued));

        assertThatThrownBy(() -> invoiceService.issue(21L))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("an invoice that already received payments cannot be cancelled")
    void cancelRejectsInvoiceWithPayments() {
        Invoice paid = invoice(InvoiceStatus.PARTIALLY_PAID, new BigDecimal("1000.00"), new BigDecimal("200.00"), "USD");
        when(invoiceRepository.findById(21L)).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> invoiceService.cancel(21L, "Duplicate"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo("INVOICE_HAS_PAYMENTS");
    }

    @Test
    @DisplayName("an unpaid issued invoice is cancelled and the reason is appended to the notes")
    void cancelUnpaidInvoice() {
        Invoice issued = invoice(InvoiceStatus.ISSUED, new BigDecimal("1000.00"), BigDecimal.ZERO.setScale(2), "USD");
        when(invoiceRepository.findById(21L)).thenReturn(Optional.of(issued));
        stubSaveAssigningId();

        InvoiceResponse response = invoiceService.cancel(21L, "Raised in error");

        assertThat(response.status()).isEqualTo(InvoiceStatus.CANCELLED);
        assertThat(response.notes()).contains("Raised in error");
        verify(auditService).record(eq("INVOICE"), eq(21L), eq(AuditActions.INVOICE_CANCELLED), any(), any(), eq("Raised in error"));
    }

    @Test
    @DisplayName("settlement recalculation marks a fully settled invoice PAID")
    void refreshSettlementStateMarksPaid() {
        Invoice issued = invoice(InvoiceStatus.ISSUED, new BigDecimal("1000.00"), BigDecimal.ZERO.setScale(2), "USD");
        issued.setDueDate(LocalDate.now().plusDays(5));
        when(invoiceRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(issued));
        when(paymentRepository.sumCompletedByInvoiceId(21L)).thenReturn(new BigDecimal("1000.00"));
        stubSaveAssigningId();

        Invoice result = invoiceService.refreshSettlementState(21L);

        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(result.getAmountPaid()).isEqualByComparingTo("1000.00");
        assertThat(result.outstandingAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("a partially settled invoice is PARTIALLY_PAID, or OVERDUE when past due")
    void refreshSettlementStatePartialAndOverdue() {
        Invoice pastDue = invoice(InvoiceStatus.ISSUED, new BigDecimal("1000.00"), BigDecimal.ZERO.setScale(2), "USD");
        when(invoiceRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(pastDue));
        when(paymentRepository.sumCompletedByInvoiceId(21L)).thenReturn(new BigDecimal("400.00"));
        stubSaveAssigningId();

        Invoice result = invoiceService.refreshSettlementState(21L);

        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);
        assertThat(result.outstandingAmount()).isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("a cancelled invoice keeps its status even when payments are recalculated")
    void refreshSettlementStateNeverUnCancels() {
        Invoice cancelled = invoice(InvoiceStatus.CANCELLED, new BigDecimal("1000.00"), BigDecimal.ZERO.setScale(2), "USD");
        when(invoiceRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(cancelled));
        when(paymentRepository.sumCompletedByInvoiceId(21L)).thenReturn(BigDecimal.ZERO);
        stubSaveAssigningId();

        Invoice result = invoiceService.refreshSettlementState(21L);

        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
    }

    @Test
    @DisplayName("outstanding balance is always total minus net completed payments")
    void outstandingBalanceUsesNetCompletedPayments() {
        Invoice partiallyPaid = invoice(InvoiceStatus.PARTIALLY_PAID, new BigDecimal("4329875.00"), new BigDecimal("1000000.00"), "USD");
        when(invoiceRepository.findById(21L)).thenReturn(Optional.of(partiallyPaid));

        assertThat(invoiceService.getOutstanding(21L).outstandingAmount()).isEqualByComparingTo("3329875.00");
    }

    @Test
    @DisplayName("marking overdue flips past-due invoices with a balance")
    void refreshOverdueInvoicesFlipsStatus() {
        Invoice pastDue = invoice(InvoiceStatus.ISSUED, new BigDecimal("1000.00"), BigDecimal.ZERO.setScale(2), "USD");
        when(invoiceRepository.findOverdueCandidates(any(LocalDate.class))).thenReturn(java.util.List.of(pastDue));
        stubSaveAssigningId();

        var updated = invoiceService.refreshOverdueInvoices(LocalDate.now());

        assertThat(updated).hasSize(1);
        assertThat(updated.get(0).getStatus()).isEqualTo(InvoiceStatus.OVERDUE);
        verify(auditService).record(eq("INVOICE"), anyLong(), eq(AuditActions.INVOICE_STATUS_CHANGED), eq(InvoiceStatus.ISSUED), eq(InvoiceStatus.OVERDUE), anyString());
    }
}
