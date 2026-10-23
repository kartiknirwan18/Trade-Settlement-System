package com.example.settlement.entity;

import com.example.settlement.enums.TradeStatus;
import com.example.settlement.enums.TradeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
 * A completed (or in-flight) physical commodity trade. Only trades in
 * {@link TradeStatus#COMPLETED} state are eligible for invoice generation.
 */
@Entity
@Table(
        name = "trades",
        uniqueConstraints = @UniqueConstraint(name = "uk_trades_reference", columnNames = "trade_reference"),
        indexes = {
                @Index(name = "idx_trades_commodity", columnList = "commodity"),
                @Index(name = "idx_trades_status", columnList = "status"),
                @Index(name = "idx_trades_trade_date", columnList = "trade_date")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trade extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_reference", nullable = false, length = 40)
    private String tradeReference;

    @Column(name = "commodity", nullable = false, length = 120)
    private String commodity;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false, length = 20)
    private TradeType tradeType;

    @Column(name = "buyer_name", nullable = false, length = 150)
    private String buyerName;

    @Column(name = "seller_name", nullable = false, length = 150)
    private String sellerName;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_of_measure", nullable = false, length = 20)
    private String unitOfMeasure;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TradeStatus status;

    @Column(name = "notes", length = 500)
    private String notes;

    /** Optimistic locking guard against concurrent modifications of the same trade. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Gross notional value of the trade: quantity x unit price. */
    public BigDecimal grossValue() {
        return quantity.multiply(unitPrice);
    }
}
