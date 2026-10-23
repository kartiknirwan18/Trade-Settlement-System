package com.example.settlement.enums;

/** Outcome of comparing the expected invoice amount with the actual settled amount. */
public enum ReconciliationStatus {
    MATCHED,
    UNDERPAID,
    OVERPAID,
    UNMATCHED,
    REVIEW_REQUIRED;

    /** Statuses that still require a human decision. */
    public boolean isUnresolved() {
        return this != MATCHED;
    }
}
