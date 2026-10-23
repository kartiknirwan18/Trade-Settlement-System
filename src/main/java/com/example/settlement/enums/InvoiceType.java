package com.example.settlement.enums;

/**
 * Direction of the money flow represented by an invoice.
 * <ul>
 *   <li>{@link #RECEIVABLE} - we are owed money (money comes in).</li>
 *   <li>{@link #PAYABLE} - we owe money (money goes out).</li>
 * </ul>
 */
public enum InvoiceType {
    RECEIVABLE,
    PAYABLE;

    /** The payment direction that settles this invoice type. */
    public PaymentDirection expectedPaymentDirection() {
        return this == RECEIVABLE ? PaymentDirection.INCOMING : PaymentDirection.OUTGOING;
    }
}
