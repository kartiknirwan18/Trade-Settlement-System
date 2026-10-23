package com.example.settlement.dto.response;

import java.time.LocalDateTime;

/** A single, immutable entry of the append-only audit trail. */
public record AuditLogResponse(
        Long id,
        String entityType,
        String entityId,
        String action,
        String previousValues,
        String newValues,
        String performedBy,
        LocalDateTime performedAt,
        String reason) {
}
