package com.example.settlement.enums;

/**
 * Lifecycle of a payment. Only {@link #COMPLETED} payments reduce the outstanding
 * balance of an invoice. Reversals are recorded as a status change so that the
 * original transaction is never deleted.
 */
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REVERSED;

    /** Only a completed payment contributes to the settled amount. */
    public boolean affectsOutstandingBalance() {
        return this == COMPLETED;
    }

    public boolean canTransitionTo(PaymentStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PENDING -> target == COMPLETED || target == FAILED;
            case COMPLETED -> target == REVERSED;
            case FAILED, REVERSED -> false;
        };
    }
}
