package com.example.settlement.audit;

import com.example.settlement.entity.AuditLog;
import com.example.settlement.repository.AuditLogRepository;
import com.example.settlement.security.CurrentUserProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Persists audit entries as part of the caller's transaction so that an audit row
 * exists if and only if the business change was committed.
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);
    private static final int MAX_VALUE_LENGTH = 3900;

    /** Masks any accidental secret material before it reaches the database. */
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(\"(?:password|passwd|pwd|secret|token|accessToken|refreshToken|jwt|authorization)\"\\s*:\\s*)\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final CurrentUserProvider currentUserProvider;

    public AuditServiceImpl(AuditLogRepository auditLogRepository,
                            ObjectMapper objectMapper,
                            CurrentUserProvider currentUserProvider) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String entityType, Object entityId, String action,
                       Object previousValues, Object newValues, String reason) {
        AuditLog entry = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId == null ? null : String.valueOf(entityId))
                .action(action)
                .previousValues(serialise(previousValues))
                .newValues(serialise(newValues))
                .performedBy(currentUserProvider.currentUsername())
                .reason(truncate(reason, 500))
                .build();
        auditLogRepository.save(entry);
        log.debug("Audit: {} {} {} by {}", entityType, entityId, action, entry.getPerformedBy());
    }

    private String serialise(Object value) {
        if (value == null) {
            return null;
        }
        String json;
        if (value instanceof CharSequence cs) {
            json = cs.toString();
        } else {
            try {
                json = objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                json = String.valueOf(value);
            }
        }
        return truncate(SECRET_PATTERN.matcher(json).replaceAll("$1\"***REDACTED***\""), MAX_VALUE_LENGTH);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "...(truncated)";
    }
}
