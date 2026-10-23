package com.example.settlement.audit;

/**
 * Records financially significant operations in the append-only audit trail.
 * Implementations must never persist secrets (passwords, tokens, card data).
 */
public interface AuditService {

    /**
     * @param entityType      business entity name, e.g. {@code TRADE}
     * @param entityId        identifier of the affected record
     * @param action          one of the {@link AuditActions} constants
     * @param previousValues  state before the change (nullable, serialised to JSON)
     * @param newValues       state after the change (nullable, serialised to JSON)
     * @param reason          free text justification or notes
     */
    void record(String entityType, Object entityId, String action, Object previousValues, Object newValues, String reason);

    /** Convenience overload for an informational entry with no previous state. */
    default void record(String entityType, Object entityId, String action, Object newValues, String reason) {
        record(entityType, entityId, action, null, newValues, reason);
    }
}
