package com.example.settlement.audit;

/** Canonical audit action names. Kept as constants so logs stay queryable and consistent. */
public final class AuditActions {

    public static final String TRADE_CREATED = "TRADE_CREATED";
    public static final String TRADE_UPDATED = "TRADE_UPDATED";
    public static final String TRADE_STATUS_CHANGED = "TRADE_STATUS_CHANGED";

    public static final String INVOICE_GENERATED = "INVOICE_GENERATED";
    public static final String INVOICE_ISSUED = "INVOICE_ISSUED";
    public static final String INVOICE_CANCELLED = "INVOICE_CANCELLED";
    public static final String INVOICE_STATUS_CHANGED = "INVOICE_STATUS_CHANGED";

    public static final String PAYMENT_CREATED = "PAYMENT_CREATED";
    public static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_REVERSED = "PAYMENT_REVERSED";

    public static final String RECONCILIATION_RUN = "RECONCILIATION_RUN";
    public static final String RECONCILIATION_RESOLVED = "RECONCILIATION_RESOLVED";

    public static final String ALERT_CREATED = "ALERT_CREATED";
    public static final String ALERT_ACKNOWLEDGED = "ALERT_ACKNOWLEDGED";

    public static final String USER_CREATED = "USER_CREATED";

    private AuditActions() {
    }
}
