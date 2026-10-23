package com.example.settlement.enums;

/** Lifecycle of an invoice. */
public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PARTIALLY_PAID,
    PAID,
    OVERDUE,
    CANCELLED;

    public boolean isCancelled() {
        return this == CANCELLED;
    }

    /** Cancelled and fully paid invoices are closed for further settlement activity. */
    public boolean isSettledOrClosed() {
        return this == PAID || this == CANCELLED;
    }

    public boolean allowsPayment() {
        return this == ISSUED || this == PARTIALLY_PAID || this == OVERDUE;
    }

    /** Guard for the documented invoice state machine. */
    public boolean canTransitionTo(InvoiceStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case DRAFT -> target == ISSUED || target == CANCELLED;
            case ISSUED -> target == PARTIALLY_PAID || target == PAID || target == OVERDUE || target == CANCELLED;
            case PARTIALLY_PAID -> target == PAID || target == OVERDUE || target == PARTIALLY_PAID;
            case OVERDUE -> target == PARTIALLY_PAID || target == PAID;
            case PAID -> false;
            case CANCELLED -> false;
        };
    }
}
