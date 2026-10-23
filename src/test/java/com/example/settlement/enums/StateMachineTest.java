package com.example.settlement.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the trade and invoice state machines and their guard predicates. */
class StateMachineTest {

    @Test
    @DisplayName("only COMPLETED trades are eligible for invoicing")
    void tradeInvoiceEligibility() {
        assertThat(TradeStatus.COMPLETED.isInvoiceEligible()).isTrue();
        assertThat(TradeStatus.DRAFT.isInvoiceEligible()).isFalse();
        assertThat(TradeStatus.CONFIRMED.isInvoiceEligible()).isFalse();
        assertThat(TradeStatus.CANCELLED.isInvoiceEligible()).isFalse();
    }

    @Test
    @DisplayName("trade transitions follow DRAFT -> CONFIRMED -> COMPLETED")
    void tradeTransitions() {
        assertThat(TradeStatus.DRAFT.canTransitionTo(TradeStatus.CONFIRMED)).isTrue();
        assertThat(TradeStatus.DRAFT.canTransitionTo(TradeStatus.CANCELLED)).isTrue();
        assertThat(TradeStatus.CONFIRMED.canTransitionTo(TradeStatus.COMPLETED)).isTrue();

        // Skipping the confirmation step is not allowed.
        assertThat(TradeStatus.DRAFT.canTransitionTo(TradeStatus.COMPLETED)).isFalse();
        // Completed and cancelled trades are terminal.
        assertThat(TradeStatus.COMPLETED.canTransitionTo(TradeStatus.CANCELLED)).isFalse();
        assertThat(TradeStatus.CANCELLED.canTransitionTo(TradeStatus.CONFIRMED)).isFalse();
        assertThat(TradeStatus.CANCELLED.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(TradeStatus.class)
    @DisplayName("a status always transitions to itself")
    void tradeSelfTransitionIsAllowed(TradeStatus status) {
        assertThat(status.canTransitionTo(status)).isTrue();
    }

    @Test
    @DisplayName("payments are only accepted on ISSUED, PARTIALLY_PAID and OVERDUE invoices")
    void invoicePaymentEligibility() {
        assertThat(InvoiceStatus.ISSUED.allowsPayment()).isTrue();
        assertThat(InvoiceStatus.PARTIALLY_PAID.allowsPayment()).isTrue();
        assertThat(InvoiceStatus.OVERDUE.allowsPayment()).isTrue();
        assertThat(InvoiceStatus.DRAFT.allowsPayment()).isFalse();
        assertThat(InvoiceStatus.PAID.allowsPayment()).isFalse();
        assertThat(InvoiceStatus.CANCELLED.allowsPayment()).isFalse();
    }

    @Test
    @DisplayName("invoice transitions follow the documented lifecycle")
    void invoiceTransitions() {
        assertThat(InvoiceStatus.DRAFT.canTransitionTo(InvoiceStatus.ISSUED)).isTrue();
        assertThat(InvoiceStatus.ISSUED.canTransitionTo(InvoiceStatus.PARTIALLY_PAID)).isTrue();
        assertThat(InvoiceStatus.ISSUED.canTransitionTo(InvoiceStatus.OVERDUE)).isTrue();
        assertThat(InvoiceStatus.PARTIALLY_PAID.canTransitionTo(InvoiceStatus.PAID)).isTrue();
        assertThat(InvoiceStatus.OVERDUE.canTransitionTo(InvoiceStatus.PAID)).isTrue();

        // A draft cannot jump straight to paid, and paid/cancelled are final.
        assertThat(InvoiceStatus.DRAFT.canTransitionTo(InvoiceStatus.PAID)).isFalse();
        assertThat(InvoiceStatus.PAID.canTransitionTo(InvoiceStatus.OVERDUE)).isFalse();
        assertThat(InvoiceStatus.CANCELLED.canTransitionTo(InvoiceStatus.ISSUED)).isFalse();
        assertThat(InvoiceStatus.PAID.isSettledOrClosed()).isTrue();
    }

    @Test
    @DisplayName("invoice type determines the expected payment direction")
    void invoiceTypeDrivesPaymentDirection() {
        assertThat(InvoiceType.RECEIVABLE.expectedPaymentDirection()).isEqualTo(PaymentDirection.INCOMING);
        assertThat(InvoiceType.PAYABLE.expectedPaymentDirection()).isEqualTo(PaymentDirection.OUTGOING);
    }
}
