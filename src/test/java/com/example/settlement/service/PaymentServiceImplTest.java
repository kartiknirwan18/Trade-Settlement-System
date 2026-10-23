package com.example.settlement.service;

import com.example.settlement.audit.AuditActions;
import com.example.settlement.audit.AuditService;
import com.example.settlement.config.SettlementProperties;
import com.example.settlement.dto.request.PaymentCreateRequest;
import com.example.settlement.dto.response.PaymentResponse;
import com.example.settlement.entity.Invoice;
import com.example.settlement.entity.Payment;
import com.example.settlement.entity.Trade;
import com.example.settlement.enums.InvoiceStatus;
import com.example.settlement.enums.InvoiceType;
import com.example.settlement.enums.PaymentDirection;
import com.example.settlement.enums.PaymentMethod;
import com.example.settlement.enums.PaymentStatus;
import com.example.settlement.enums.TradeStatus;
import com.example.settlement.enums.TradeType;
import com.example.settlement.exception.BusinessRuleException;
import com.example.settlement.exception.CurrencyMismatchException;
import com.example.settlement.exception.DuplicateResourceException;
import com.example.settlement.exception.InvalidStateTransitionException;
import com.example.settlement.repository.PaymentRepository;
import com.example.settlement.service.impl.PaymentServiceImpl;
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

/** Unit tests for payment recording, settlement impact and the payment lifecycle. */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final BigDecimal TOTAL = new BigDecimal("1000.00");

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private AuditService auditService;

    private SettlementProperties properties;
    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        properties = new SettlementProperties();
        paymentService = new PaymentServiceImpl(paymentRepository, invoiceService, properties, auditService);
    }

    private static Invoice invoice(InvoiceStatus status, InvoiceType type, BigDecimal total, BigDecimal paid, String currency) {
        return Invoice.builder()
                .id(21L)
                .invoiceNumber("INV-2026-000123")
                .trade(Trade.builder().id(11L).tradeReference("TRD-1").tradeType(TradeType.SELL)
                        .status(TradeStatus.COMPLETED).build())
                .tradeReference("TRD-1")
                .invoiceType(type)
                .buyerName("Shell Trading Ltd")
                .sellerName("BP Energy")
                .invoiceDate(LocalDate.now().minusDays(10))
                .dueDate(LocalDate.now().plusDays(20))
                .commodityDescription("Brent Crude Oil")
                .quantity(new BigDecimal("10.0000"))
                .unitPrice(new BigDecimal("100.0000"))
                .subtotal(total)
                .taxPercentage(BigDecimal.ZERO.setScale(2))
                .taxAmount(BigDecimal.ZERO.setScale(2))
                .additionalCharges(BigDecimal.ZERO.setScale(2))
                .discountAmount(BigDecimal.ZERO.setScale(2))
                .totalAmount(total)
                .amountPaid(paid)
                .currencyCode(currency)
                .status(status)
                .build();
    }

    private static PaymentCreateRequest request(BigDecimal amount, PaymentStatus status, PaymentDirection direction,
                                                String currency, String reference) {
        return new PaymentCreateRequest(21L, direction, reference, LocalDate.now(), amount, currency,
                PaymentMethod.BANK_TRANSFER, "SWIFT-REF-1", status, "Test payment");
    }

    private void stubPaymentSaveAssigningId() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment toSave = invocation.getArgument(0);
            if (toSave.getId() == null) {
                toSave.setId(31L);
            }
            return toSave;
        });
    }

    @Test
    @DisplayName("a PENDING payment is recorded but does not change the invoice balance")
    void recordsPendingPaymentWithoutSettlementImpact() {
        Invoice issued = invoice(InvoiceStatus.ISSUED, InvoiceType.RECEIVABLE, TOTAL, BigDecimal.ZERO.setScale(2), "USD");
        when(invoiceService.requireInvoice(21L)).thenReturn(issued);
        when(paymentRepository.existsByPaymentReference(anyString())).thenReturn(false);
        stubPaymentSaveAssigningId();

        PaymentResponse response = paymentService.create(request(new BigDecimal("400.00"), null, null, null, null));

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.direction()).isEqualTo(PaymentDirection.INCOMING);
        assertThat(response.currencyCode()).isEqualTo("USD");
        assertThat(response.paymentReference()).startsWith("PAY-");
        verify(invoiceService, never()).refreshSettlementState(any());
    }

    @Test
    @DisplayName("a COMPLETED payment immediately recalculates the invoice settlement state")
    void recordsCompletedPaymentAndRefreshesSettlement() {
        Invoice issued = invoice(InvoiceStatus.ISSUED, InvoiceType.RECEIVABLE, TOTAL, BigDecimal.ZERO.setScale(2), "USD");
        when(invoiceService.requireInvoice(21L)).thenReturn(issued);
        when(paymentRepository.existsByPaymentReference("PAY-MANUAL-1")).thenReturn(false);
        when(invoiceService.refreshSettlementState(21L)).thenReturn(issued);
        stubPaymentSaveAssigningId();

        PaymentResponse response = paymentService.create(
                request(new BigDecimal("400.00"), PaymentStatus.COMPLETED, PaymentDirection.INCOMING, "USD", "PAY-MANUAL-1"));

        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        verify(invoiceService).refreshSettlementState(21L);
        verify(auditService).record(eq("PAYMENT"), eq(31L), eq(AuditActions.PAYMENT_CREATED), isNull(), any(), anyString());
    }

    @Test
    @DisplayName("a payment direction that contradicts the invoice type is rejected")
    void rejectsDirectionMismatch() {
        Invoice issued = invoice(InvoiceStatus.ISSUED, InvoiceType.RECEIVABLE, TOTAL, BigDecimal.ZERO.setScale(2), "USD");
        when(invoiceService.requireInvoice(21L)).thenReturn(issued);

        assertThatThrownBy(() -> paymentService.create(
                request(new BigDecimal("100.00"), null, PaymentDirection.OUTGOING, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo("PAYMENT_DIRECTION_MISMATCH");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("a payment in a currency different from the invoice is rejected")
    void rejectsCurrencyMismatch() {
        Invoice issued = invoice(InvoiceStatus.ISSUED, InvoiceType.RECEIVABLE, TOTAL, BigDecimal.ZERO.setScale(2), "USD");
        when(invoiceService.requireInvoice(21L)).thenReturn(issued);

        assertThatThrownBy(() -> paymentService.create(
                request(new BigDecimal("100.00"), null, null, "EUR", null)))
                .isInstanceOf(CurrencyMismatchException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("a completed payment above the outstanding balance is rejected by default")
    void rejectsOverpayment() {
        Invoice partiallyPaid = invoice(InvoiceStatus.PARTIALLY_PAID, InvoiceType.RECEIVABLE, TOTAL, new BigDecimal("900.00"), "USD");
        when(invoiceService.requireInvoice(21L)).thenReturn(partiallyPaid);

        assertThatThrownBy(() -> paymentService.create(
                request(new BigDecimal("150.00"), PaymentStatus.COMPLETED, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo("OVERPAYMENT_NOT_ALLOWED");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("overpayment is permitted when settlement.payment.allow-overpayment is enabled")
    void allowsOverpaymentWhenConfigured() {
        properties.getPayment().setAllowOverpayment(true);
        Invoice partiallyPaid = invoice(InvoiceStatus.PARTIALLY_PAID, InvoiceType.RECEIVABLE, TOTAL, new BigDecimal("900.00"), "USD");
        when(invoiceService.requireInvoice(21L)).thenReturn(partiallyPaid);
        when(paymentRepository.existsByPaymentReference(anyString())).thenReturn(false);
        when(invoiceService.refreshSettlementState(21L)).thenReturn(partiallyPaid);
        stubPaymentSaveAssigningId();

        PaymentResponse response = paymentService.create(
                request(new BigDecimal("150.00"), PaymentStatus.COMPLETED, null, null, null));

        assertThat(response.amount()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("a payment cannot be created directly in REVERSED status")
    void rejectsReversedOnCreation() {
        Invoice issued = invoice(InvoiceStatus.ISSUED, InvoiceType.RECEIVABLE, TOTAL, BigDecimal.ZERO.setScale(2), "USD");
        when(invoiceService.requireInvoice(21L)).thenReturn(issued);

        assertThatThrownBy(() -> paymentService.create(
                request(new BigDecimal("100.00"), PaymentStatus.REVERSED, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo("INVALID_PAYMENT_STATUS");
    }

    @Test
    @DisplayName("a duplicate payment reference is rejected")
    void rejectsDuplicateReference() {
        Invoice issued = invoice(InvoiceStatus.ISSUED, InvoiceType.RECEIVABLE, TOTAL, BigDecimal.ZERO.setScale(2), "USD");
        when(invoiceService.requireInvoice(21L)).thenReturn(issued);
        when(paymentRepository.existsByPaymentReference("PAY-DUP")).thenReturn(true);

        assertThatThrownBy(() -> paymentService.create(
                request(new BigDecimal("100.00"), null, null, null, "PAY-DUP")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("paymentReference");
    }

    @Test
    @DisplayName("a zero or negative amount is rejected")
    void rejectsNonPositiveAmount() {
        Invoice issued = invoice(InvoiceStatus.ISSUED, InvoiceType.RECEIVABLE, TOTAL, BigDecimal.ZERO.setScale(2), "USD");
        when(invoiceService.requireInvoice(21L)).thenReturn(issued);

        assertThatThrownBy(() -> paymentService.create(request(BigDecimal.ZERO, null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo("INVALID_AMOUNT");
    }

    @Test
    @DisplayName("a draft invoice cannot accept payments")
    void rejectsPaymentOnDraftInvoice() {
        Invoice draft = invoice(InvoiceStatus.DRAFT, InvoiceType.RECEIVABLE, TOTAL, BigDecimal.ZERO.setScale(2), "USD");
        when(invoiceService.requireInvoice(21L)).thenReturn(draft);

        assertThatThrownBy(() -> paymentService.create(request(new BigDecimal("100.00"), null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo("INVOICE_NOT_PAYABLE");
    }

    @Test
    @DisplayName("a PAYABLE invoice expects an outgoing payment")
    void payableInvoiceExpectsOutgoingPayment() {
        Invoice payable = invoice(InvoiceStatus.ISSUED, InvoiceType.PAYABLE, TOTAL, BigDecimal.ZERO.setScale(2), "USD");
        when(invoiceService.requireInvoice(21L)).thenReturn(payable);
        when(paymentRepository.existsByPaymentReference(anyString())).thenReturn(false);
        stubPaymentSaveAssigningId();

        PaymentResponse response = paymentService.create(
                request(new BigDecimal("250.00"), null, null, null, null));

        assertThat(response.direction()).isEqualTo(PaymentDirection.OUTGOING);
    }

    @Test
    @DisplayName("completing a payment moves it to COMPLETED and refreshes the invoice")
    void completesPendingPayment() {
        Invoice issued = invoice(InvoiceStatus.ISSUED, InvoiceType.RECEIVABLE, TOTAL, BigDecimal.ZERO.setScale(2), "USD");
        Payment pending = Payment.builder().id(31L).paymentReference("PAY-1").invoice(issued)
                .direction(PaymentDirection.INCOMING).paymentDate(LocalDate.now())
                .amount(new BigDecimal("400.00")).currencyCode("USD").paymentMethod(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.PENDING).build();
        when(paymentRepository.findById(31L)).thenReturn(Optional.of(pending));
        when(invoiceService.requireInvoice(21L)).thenReturn(issued);
        when(invoiceService.refreshSettlementState(21L)).thenReturn(issued);
        stubPaymentSaveAssigningId();

        PaymentResponse response = paymentService.complete(31L);

        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        verify(invoiceService).refreshSettlementState(21L);
        verify(auditService).record(eq("PAYMENT"), eq(31L), eq(AuditActions.PAYMENT_COMPLETED), eq(PaymentStatus.PENDING), eq(PaymentStatus.COMPLETED), anyString());
    }

    @Test
    @DisplayName("only a PENDING payment can be completed")
    void completeRejectsNonPendingPayment() {
        Invoice issued = invoice(InvoiceStatus.ISSUED, InvoiceType.RECEIVABLE, TOTAL, BigDecimal.ZERO.setScale(2), "USD");
        Payment completed = Payment.builder().id(31L).paymentReference("PAY-1").invoice(issued)
                .status(PaymentStatus.COMPLETED).build();
        when(paymentRepository.findById(31L)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> paymentService.complete(31L))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("a failed payment keeps its row and does not settle the invoice")
    void marksPaymentFailed() {
        Invoice issued = invoice(InvoiceStatus.ISSUED, InvoiceType.RECEIVABLE, TOTAL, BigDecimal.ZERO.setScale(2), "USD");
        Payment pending = Payment.builder().id(31L).paymentReference("PAY-1").invoice(issued)
                .status(PaymentStatus.PENDING).amount(new BigDecimal("400.00")).build();
        when(paymentRepository.findById(31L)).thenReturn(Optional.of(pending));
        stubPaymentSaveAssigningId();

        PaymentResponse response = paymentService.fail(31L, "Insufficient funds");

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.reversalReason()).isEqualTo("Insufficient funds");
        verify(invoiceService, never()).refreshSettlementState(any());
    }

    @Test
    @DisplayName("reversing a completed payment restores the outstanding balance")
    void reversesCompletedPayment() {
        Invoice partiallyPaid = invoice(InvoiceStatus.PARTIALLY_PAID, InvoiceType.RECEIVABLE, TOTAL, new BigDecimal("400.00"), "USD");
        Payment completed = Payment.builder().id(31L).paymentReference("PAY-1").invoice(partiallyPaid)
                .direction(PaymentDirection.INCOMING).paymentDate(LocalDate.now())
                .amount(new BigDecimal("400.00")).currencyCode("USD").paymentMethod(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.COMPLETED).build();
        when(paymentRepository.findById(31L)).thenReturn(Optional.of(completed));
        when(invoiceService.refreshSettlementState(21L)).thenReturn(partiallyPaid);
        stubPaymentSaveAssigningId();

        PaymentResponse response = paymentService.reverse(31L, "Returned by bank");

        assertThat(response.status()).isEqualTo(PaymentStatus.REVERSED);
        assertThat(response.reversedAt()).isNotNull();
        verify(invoiceService).refreshSettlementState(21L);
        verify(auditService).record(eq("PAYMENT"), eq(31L), eq(AuditActions.PAYMENT_REVERSED), eq(PaymentStatus.COMPLETED), eq(PaymentStatus.REVERSED), anyString());
    }

    @Test
    @DisplayName("only a COMPLETED payment can be reversed")
    void reverseRejectsNonCompletedPayment() {
        Invoice issued = invoice(InvoiceStatus.ISSUED, InvoiceType.RECEIVABLE, TOTAL, BigDecimal.ZERO.setScale(2), "USD");
        Payment pending = Payment.builder().id(31L).paymentReference("PAY-1").invoice(issued)
                .status(PaymentStatus.PENDING).build();
        when(paymentRepository.findById(31L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> paymentService.reverse(31L, "Nope"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}
