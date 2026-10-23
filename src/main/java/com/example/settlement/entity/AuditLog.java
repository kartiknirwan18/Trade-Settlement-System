package com.example.settlement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Append-only audit trail of financially significant operations.
 *
 * <p>Rows can be inserted through the application but can never be updated or
 * deleted - the {@link PreUpdate}/{@link PreRemove} callbacks enforce this at the
 * persistence layer in addition to the absence of any modifying REST endpoint.</p>
 *
 * <p>Secrets (passwords, JWT tokens, card data) must never be written here.</p>
 */
@Entity
@Table(
        name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_entity", columnList = "entity_type,entity_id"),
                @Index(name = "idx_audit_performed_by", columnList = "performed_by"),
                @Index(name = "idx_audit_performed_at", columnList = "performed_at")
        })
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "previous_values", length = 4000)
    private String previousValues;

    @Column(name = "new_values", length = 4000)
    private String newValues;

    @Column(name = "performed_by", nullable = false, length = 80)
    private String performedBy;

    @CreationTimestamp
    @Column(name = "performed_at", nullable = false, updatable = false)
    private LocalDateTime performedAt;

    @Column(name = "reason", length = 500)
    private String reason;

    @PreUpdate
    void preventUpdate() {
        throw new UnsupportedOperationException("Audit records are append-only and cannot be modified");
    }

    @PreRemove
    void preventRemoval() {
        throw new UnsupportedOperationException("Audit records are append-only and cannot be deleted");
    }
}
