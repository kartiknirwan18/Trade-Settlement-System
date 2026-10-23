package com.example.settlement.util;

import com.example.settlement.config.SettlementProperties;
import com.example.settlement.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the single source of truth of invoice arithmetic:
 *
 * <pre>
 *   Subtotal = Quantity x Unit Price
 *   Tax      = Subtotal x Tax Percentage / 100
 *   Total    = Subtotal + Tax + Additional Charges - Discount
 * </pre>
 */
class InvoiceCalculatorTest {

    private SettlementProperties properties;
    private InvoiceCalculator calculator;

    @BeforeEach
    void setUp() {
        properties = new SettlementProperties();
        calculator = new InvoiceCalculator(properties);
    }

    @Nested
    @DisplayName("Formula")
    class Formula {

        @Test
        @DisplayName("subtotal, tax and total follow the documented formula")
        void computesDocumentedFormula() {
            InvoiceCalculator.Amounts amounts = calculator.calculate(
                    new BigDecimal("50000"),
                    new BigDecimal("82.45"),
                    new BigDecimal("5.00"),
                    new BigDecimal("1500.00"),
                    new BigDecimal("250.00"));

            assertThat(amounts.subtotal()).isEqualByComparingTo("4122500.00");
            assertThat(amounts.taxAmount()).isEqualByComparingTo("206125.00");
            // 4 122 500.00 + 206 125.00 + 1 500.00 - 250.00
            assertThat(amounts.totalAmount()).isEqualByComparingTo("4329875.00");
        }

        @Test
        @DisplayName("null tax, charges and discount are treated as zero")
        void treatsNullsAsZero() {
            InvoiceCalculator.Amounts amounts = calculator.calculate(
                    new BigDecimal("120"), new BigDecimal("2.50"), null, null, null);

            assertThat(amounts.subtotal()).isEqualByComparingTo("300.00");
            assertThat(amounts.taxAmount()).isEqualByComparingTo("0.00");
            assertThat(amounts.totalAmount()).isEqualByComparingTo("300.00");
        }

        @Test
        @DisplayName("tax is rounded HALF_UP to the configured amount scale")
        void roundsTaxHalfUp() {
            InvoiceCalculator.Amounts amounts = calculator.calculate(
                    new BigDecimal("10"),
                    new BigDecimal("3.333"),
                    new BigDecimal("7.5"),
                    null,
                    null);

            assertThat(amounts.subtotal()).isEqualByComparingTo("33.33");
            // 33.33 x 7.5 / 100 = 2.49975 -> 2.50
            assertThat(amounts.taxAmount()).isEqualByComparingTo("2.50");
            assertThat(amounts.totalAmount()).isEqualByComparingTo("35.83");
        }

        @Test
        @DisplayName("zero tax percentage produces no tax")
        void zeroTaxProducesNoTax() {
            InvoiceCalculator.Amounts amounts = calculator.calculate(
                    new BigDecimal("100"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            assertThat(amounts.taxAmount()).isEqualByComparingTo("0.00");
            assertThat(amounts.totalAmount()).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("the configured scale and rounding mode drive every result")
        void honoursConfiguredScaleAndRoundingMode() {
            properties.getInvoice().setAmountScale(4);
            properties.getInvoice().setRoundingMode("DOWN");
            InvoiceCalculator custom = new InvoiceCalculator(properties);

            InvoiceCalculator.Amounts amounts = custom.calculate(
                    new BigDecimal("3"), new BigDecimal("1.11111"), null, null, null);

            assertThat(amounts.subtotal()).isEqualByComparingTo("3.3333");
            assertThat(custom.scale()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "-0.0001"})
        @DisplayName("quantity must be strictly positive")
        void rejectsNonPositiveQuantity(String quantity) {
            assertThatThrownBy(() -> calculator.calculate(
                    new BigDecimal(quantity), new BigDecimal("10"), null, null, null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("quantity");
        }

        @Test
        @DisplayName("null quantity is rejected")
        void rejectsNullQuantity() {
            assertThatThrownBy(() -> calculator.calculate(null, new BigDecimal("10"), null, null, null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("quantity");
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-5"})
        @DisplayName("unit price must be strictly positive")
        void rejectsNonPositiveUnitPrice(String unitPrice) {
            assertThatThrownBy(() -> calculator.calculate(
                    new BigDecimal("10"), new BigDecimal(unitPrice), null, null, null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("unitPrice");
        }

        @Test
        @DisplayName("a negative tax percentage is rejected")
        void rejectsNegativeTax() {
            assertThatThrownBy(() -> calculator.calculate(
                    new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("-1"), null, null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("taxPercentage");
        }

        @Test
        @DisplayName("a negative discount is rejected")
        void rejectsNegativeDiscount() {
            assertThatThrownBy(() -> calculator.calculate(
                    new BigDecimal("10"), new BigDecimal("10"), null, null, new BigDecimal("-1")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("discountAmount");
        }

        @Test
        @DisplayName("a negative additional charge is rejected")
        void rejectsNegativeCharges() {
            assertThatThrownBy(() -> calculator.calculate(
                    new BigDecimal("10"), new BigDecimal("10"), null, new BigDecimal("-1"), null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("additionalCharges");
        }

        @Test
        @DisplayName("a discount larger than the invoice produces a negative total and is rejected")
        void rejectsNegativeTotal() {
            assertThatThrownBy(() -> calculator.calculate(
                    new BigDecimal("1"), new BigDecimal("100"), null, null, new BigDecimal("150")))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                    .isEqualTo("NEGATIVE_INVOICE_TOTAL");
        }
    }

    @Nested
    @DisplayName("Helpers")
    class Helpers {

        @Test
        @DisplayName("round() normalises monetary values to the configured scale")
        void roundsMonetaryValues() {
            assertThat(calculator.round(new BigDecimal("10.005"))).isEqualByComparingTo("10.01");
            assertThat(calculator.round(null)).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("roundTaxPercentage() uses the tax scale")
        void roundsTaxPercentages() {
            properties.getInvoice().setTaxScale(3);
            InvoiceCalculator custom = new InvoiceCalculator(properties);
            assertThat(custom.roundTaxPercentage(new BigDecimal("7.12345"))).isEqualByComparingTo("7.123");
        }
    }
}
