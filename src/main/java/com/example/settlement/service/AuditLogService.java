package com.example.settlement.service;

import com.example.settlement.dto.common.PageResponse;
import com.example.settlement.dto.response.AuditLogResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/** Read-only access to the append-only audit trail. */
public interface AuditLogService {

    PageResponse<AuditLogResponse> search(String entityType,
                                          String entityId,
                                          String action,
                                          String performedBy,
                                          LocalDate performedAtFrom,
                                          LocalDate performedAtTo,
                                          Pageable pageable);

    /** Full change history of one business record, newest first. */
    List<AuditLogResponse> findByEntity(String entityType, String entityId);
}
