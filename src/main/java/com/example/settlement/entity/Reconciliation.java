package com.example.settlement.entity;

import com.example.settlement.enums.ReconciliationStatus;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Result of comparing the expected invoice amount against the amount actually
 * settled. Every run is appended so the full reconciliation history is kept.
 */
@Entity
@Table(
        name = "reconciliations",
        indexes = {
                @Index(name = "idx_reconciliations_invoice", columnList = "invoice_id"),
                @Index(name = "idx_reconciliations_status", columnList = "status"),
                @Index(name = "idx_reconciliations_date", columnList = "reconciliation_date")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reconciliation extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reconciliations_invoice"))
    private Invoice invoice;

    @Column(name = "expected_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedAmount;

    @Column(name = "actual_settled_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal actualSettledAmount;

    /** actual settled amount - expected amount */
    @Column(name = "variance_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal varianceAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "reconciliation_date", nullable = false)
    private LocalDate reconciliationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReconciliationStatus status;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    @Column(name = "resolution_notes", length = 500)
    private String resolutionNotes;

    @Column(name = "resolved_by", length = 80)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
