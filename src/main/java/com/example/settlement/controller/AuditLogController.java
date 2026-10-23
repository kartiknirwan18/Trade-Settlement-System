package com.example.settlement.controller;

import com.example.settlement.dto.common.ApiResponse;
import com.example.settlement.dto.common.PageResponse;
import com.example.settlement.dto.response.AuditLogResponse;
import com.example.settlement.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit", description = "Read-only, append-only audit history of financially significant operations")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AUDITOR','ADMIN','FINANCE')")
    @Operation(summary = "Search the audit trail",
            description = "Filter by entity, entity id, action, user and performed-at date range.")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> search(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String performedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate performedAtFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate performedAtTo,
            @PageableDefault(size = 20, sort = "performedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(auditLogService.search(entityType, entityId, action, performedBy,
                performedAtFrom, performedAtTo, pageable)));
    }

    @GetMapping("/{entityType}/{entityId}")
    @PreAuthorize("hasAnyRole('AUDITOR','ADMIN','FINANCE')")
    @Operation(summary = "Get the change history of one record",
            description = "Returns every audit entry for the given entity type and id, newest first.")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> findByEntity(@PathVariable String entityType,
                                                                           @PathVariable String entityId) {
        return ResponseEntity.ok(ApiResponse.ok(auditLogService.findByEntity(entityType, entityId)));
    }
}
