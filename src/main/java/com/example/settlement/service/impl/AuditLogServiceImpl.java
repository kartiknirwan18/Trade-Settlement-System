package com.example.settlement.service.impl;

import com.example.settlement.dto.common.PageResponse;
import com.example.settlement.dto.response.AuditLogResponse;
import com.example.settlement.entity.AuditLog;
import com.example.settlement.mapper.AuditLogMapper;
import com.example.settlement.repository.AuditLogRepository;
import com.example.settlement.service.AuditLogService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(String entityType, String entityId, String action, String performedBy,
                                                 LocalDate performedAtFrom, LocalDate performedAtTo, Pageable pageable) {
        Specification<AuditLog> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(entityType)) {
                predicates.add(cb.equal(cb.upper(root.get("entityType")),
                        entityType.trim().toUpperCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(entityId)) {
                predicates.add(cb.equal(root.get("entityId"), entityId.trim()));
            }
            if (StringUtils.hasText(action)) {
                predicates.add(cb.equal(cb.upper(root.get("action")), action.trim().toUpperCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(performedBy)) {
                predicates.add(cb.equal(root.get("performedBy"), performedBy.trim()));
            }
            if (performedAtFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("performedAt"), performedAtFrom.atStartOfDay()));
            }
            if (performedAtTo != null) {
                predicates.add(cb.lessThan(root.get("performedAt"), performedAtTo.plusDays(1).atStartOfDay()));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<AuditLog> page = auditLogRepository.findAll(specification, pageable);
        return PageResponse.from(page, AuditLogMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogResponse> findByEntity(String entityType, String entityId) {
        return auditLogRepository
                .findByEntityTypeAndEntityIdOrderByPerformedAtDesc(entityType.trim().toUpperCase(Locale.ROOT), entityId.trim())
                .stream()
                .map(AuditLogMapper::toResponse)
                .toList();
    }
}
