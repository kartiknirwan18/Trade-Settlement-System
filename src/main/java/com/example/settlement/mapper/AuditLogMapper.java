package com.example.settlement.mapper;

import com.example.settlement.dto.response.AuditLogResponse;
import com.example.settlement.entity.AuditLog;

/** Converts {@link AuditLog} entities into API DTOs. */
public final class AuditLogMapper {

    private AuditLogMapper() {
    }

    public static AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getEntityType(),
                log.getEntityId(),
                log.getAction(),
                log.getPreviousValues(),
                log.getNewValues(),
                log.getPerformedBy(),
                log.getPerformedAt(),
                log.getReason());
    }
}
