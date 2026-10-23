package com.example.settlement.entity;

import com.example.settlement.enums.AlertSeverity;
import com.example.settlement.enums.AlertType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Alert raised by the overdue monitoring job.
 *
 * <p>The unique constraint on (invoice_id, alert_type) is what prevents the
 * scheduled job from creating duplicate alerts for the same overdue condition.</p>
 */
@Entity
@Table(
        name = "overdue_alerts",
        uniqueConstraints = @UniqueConstraint(name = "uk_alerts_invoice_type", columnNames = {"invoice_id", "alert_type"}),
        indexes = {
                @Index(name = "idx_alerts_invoice", columnList = "invoice_id"),
                @Index(name = "idx_alerts_acknowledged", columnList = "acknowledged"),
                @Index(name = "idx_alerts_severity", columnList = "severity")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OverdueAlert extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, foreignKey = @ForeignKey(name = "fk_alerts_invoice"))
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 30)
    private AlertType alertType;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "trigger_date", nullable = false)
    private LocalDate triggerDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private AlertSeverity severity;

    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged;

    @Column(name = "acknowledged_by", length = 80)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;
}
