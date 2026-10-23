package com.example.settlement.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Central, externally configurable business settings. Everything here can be
 * overridden through environment variables (see {@code .env.example}).
 */
@ConfigurationProperties(prefix = "settlement")
@Validated
@Getter
@Setter
public class SettlementProperties {

    private final Invoice invoice = new Invoice();
    private final Payment payment = new Payment();
    private final Reconciliation reconciliation = new Reconciliation();
    private final Overdue overdue = new Overdue();
    private final Security security = new Security();

    /** When true, realistic demo data is inserted on start-up for empty databases. */
    private boolean seedSampleData = true;

    @Getter
    @Setter
    public static class Invoice {
        /** Default tax rate applied to generated invoices. */
        private BigDecimal defaultTaxPercentage = new BigDecimal("5.00");
        /** Invoice due date = invoice date + payment term days. */
        private int defaultPaymentTermDays = 30;
        /** When false, a second invoice for the same trade is rejected. */
        private boolean allowMultipleInvoicesPerTrade = false;
        /** Rounding mode used for all monetary results. */
        private String roundingMode = "HALF_UP";
        /** Number of decimal places kept for monetary amounts. */
        private int amountScale = 2;
        /** Number of decimal places kept for tax percentages. */
        private int taxScale = 2;

        public RoundingMode roundingModeValue() {
            return RoundingMode.valueOf(roundingMode.trim().toUpperCase());
        }

        public int scale() {
            return Math.max(0, amountScale);
        }

        public int taxScale() {
            return Math.max(0, taxScale);
        }
    }

    @Getter
    @Setter
    public static class Payment {
        /** When false, a payment that pushes the settled amount above the invoice total is rejected. */
        private boolean allowOverpayment = false;
    }

    @Getter
    @Setter
    public static class Reconciliation {
        /** Absolute tolerance applied when comparing expected vs actual amounts. */
        private BigDecimal toleranceAmount = new BigDecimal("0.01");
        /** Relative tolerance (percent of expected amount) applied on top of the absolute one. */
        private BigDecimal tolerancePercentage = BigDecimal.ZERO;
    }

    @Getter
    @Setter
    public static class Overdue {
        private boolean schedulingEnabled = true;
        /** Cron expression for the overdue monitoring job (default: hourly). */
        private String cron = "0 0 * * * *";
        /** How many days ahead an UPCOMING_DUE alert is raised. */
        private int upcomingDueDays = 7;
        /** Invoices more than this many days past due are escalated to CRITICAL. */
        private int highSeverityAfterDays = 30;
    }

    @Getter
    @Setter
    public static class Security {
        private final Jwt jwt = new Jwt();

        @Getter
        @Setter
        public static class Jwt {
            /** HS256 signing secret; supplied through the JWT_SECRET environment variable. */
            private String secret = "";
            private long expirationMinutes = 120;
            private String issuer = "trade-settlement-system";
        }
    }
}
