package com.example.settlement.entity;

import com.example.settlement.enums.InvoiceStatus;
import com.example.settlement.enums.InvoiceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An invoice raised from a completed trade.
 *
 * <p>The commodity description, quantity, unit price, buyer, seller and trade
 * reference are <strong>copied</strong> onto the invoice when it is generated.
 * This is the invoice "snapshot": later edits to the trade never silently change
 * an already generated invoice.</p>
 */
@Entity
@Table(
        name = "invoices",
        uniqueConstraints = @UniqueConstraint(name = "uk_invoices_number", columnNames = "invoice_number"),
        indexes = {
                @Index(name = "idx_invoices_trade", columnList = "trade_id"),
                @Index(name = "idx_invoices_status", columnList = "status"),
                @Index(name = "idx_invoices_due_date", columnList = "due_date"),
                @Index(name = "idx_invoices_invoice_date", columnList = "invoice_date"),
                @Index(name = "idx_invoices_currency", columnList = "currency_code")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, length = 40)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trade_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_invoices_trade"))
    private Trade trade;

    /** Snapshot of {@link Trade#getTradeReference()} at generation time. */
    @Column(name = "trade_reference", nullable = false, length = 40)
    private String tradeReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false, length = 20)
    private InvoiceType invoiceType;

    @Column(name = "buyer_name", nullable = false, length = 150)
    private String buyerName;

    @Column(name = "seller_name", nullable = false, length = 150)
    private String sellerName;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "commodity_description", nullable = false, length = 200)
    private String commodityDescription;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "tax_percentage", nullable = false, precision = 9, scale = 4)
    private BigDecimal taxPercentage;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "additional_charges", nullable = false, precision = 19, scale = 4)
    private BigDecimal additionalCharges;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    /** Net of all completed payments; maintained in the same transaction as payments. */
    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountPaid;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "notes", length = 500)
    private String notes;

    /** Optimistic locking: prevents two concurrent payments from producing a wrong balance. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Outstanding amount = total - net completed payments. */
    public BigDecimal outstandingAmount() {
        BigDecimal paid = amountPaid == null ? BigDecimal.ZERO : amountPaid;
        return totalAmount.subtract(paid);
    }
}
