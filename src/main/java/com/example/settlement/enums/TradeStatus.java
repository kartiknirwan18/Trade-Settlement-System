package com.example.settlement.enums;

/**
 * Lifecycle of a trade. Only {@link #COMPLETED} trades are eligible for invoicing.
 */
public enum TradeStatus {
    DRAFT,
    CONFIRMED,
    COMPLETED,
    CANCELLED;

    /** An invoice may only be raised against a completed trade. */
    public boolean isInvoiceEligible() {
        return this == COMPLETED;
    }

    /** Terminal states cannot be modified any further. */
    public boolean isTerminal() {
        return this == CANCELLED;
    }

    public boolean canTransitionTo(TradeStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case DRAFT -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == COMPLETED || target == CANCELLED;
            case COMPLETED -> false;
            case CANCELLED -> false;
        };
    }
}
