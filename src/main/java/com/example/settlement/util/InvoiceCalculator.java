package com.example.settlement.util;

import com.example.settlement.config.SettlementProperties;
import com.example.settlement.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single source of truth for invoice arithmetic.
 *
 * <pre>
 *   Subtotal  = Quantity x Unit Price
 *   Tax       = Subtotal x Tax Percentage / 100
 *   Total     = Subtotal + Tax + Additional Charges - Discount
 * </pre>
 *
 * <p>All amounts use {@link BigDecimal} with the configured scale and rounding
 * mode (HALF_UP by default) - never float or double.</p>
 */
@Component
public class InvoiceCalculator {

    private final SettlementProperties properties;

    public InvoiceCalculator(SettlementProperties properties) {
        this.properties = properties;
    }

    /** Immutable result of an invoice calculation. */
    public record Amounts(BigDecimal subtotal, BigDecimal taxAmount, BigDecimal totalAmount) {
    }

    public Amounts calculate(BigDecimal quantity,
                             BigDecimal unitPrice,
                             BigDecimal taxPercentage,
                             BigDecimal additionalCharges,
                             BigDecimal discountAmount) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessRuleException("INVALID_QUANTITY", "quantity must be greater than zero");
        }
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new BusinessRuleException("INVALID_UNIT_PRICE", "unitPrice must be greater than zero");
        }

        BigDecimal taxRate = nullSafe(taxPercentage);
        if (taxRate.signum() < 0) {
            throw new BusinessRuleException("INVALID_TAX_PERCENTAGE", "taxPercentage must not be negative");
        }
        BigDecimal charges = nullSafe(additionalCharges);
        BigDecimal discount = nullSafe(discountAmount);
        if (charges.signum() < 0) {
            throw new BusinessRuleException("INVALID_ADDITIONAL_CHARGES", "additionalCharges must not be negative");
        }
        if (discount.signum() < 0) {
            throw new BusinessRuleException("INVALID_DISCOUNT_AMOUNT", "discountAmount must not be negative");
        }

        int scale = properties.getInvoice().scale();
        RoundingMode rounding = properties.getInvoice().roundingModeValue();

        BigDecimal subtotal = quantity.multiply(unitPrice).setScale(scale, rounding);
        BigDecimal taxAmount = subtotal
                .multiply(taxRate)
                .divide(new BigDecimal("100"), scale + 4, rounding)
                .setScale(scale, rounding);
        BigDecimal total = subtotal.add(taxAmount).add(charges).subtract(discount).setScale(scale, rounding);

        if (total.signum() < 0) {
            throw new BusinessRuleException("NEGATIVE_INVOICE_TOTAL",
                    "discountAmount and charges produce a negative invoice total (" + total + ")");
        }
        return new Amounts(subtotal, taxAmount, total);
    }

    /** Rounds a monetary value to the configured scale using the configured rounding mode. */
    public BigDecimal round(BigDecimal value) {
        return nullSafe(value).setScale(properties.getInvoice().scale(), properties.getInvoice().roundingModeValue());
    }

    public BigDecimal roundTaxPercentage(BigDecimal value) {
        return nullSafe(value).setScale(properties.getInvoice().taxScale(), properties.getInvoice().roundingModeValue());
    }

    public int scale() {
        return properties.getInvoice().scale();
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
