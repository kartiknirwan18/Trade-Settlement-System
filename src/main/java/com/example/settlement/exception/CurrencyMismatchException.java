package com.example.settlement.exception;

/**
 * Thrown when a payment or reconciliation is attempted across mismatched
 * currencies. Amounts are never compared or settled silently across currencies.
 * Maps to HTTP 400.
 */
public class CurrencyMismatchException extends BusinessRuleException {

    public CurrencyMismatchException(String expected, String actual) {
        super("CURRENCY_MISMATCH",
                "Currency mismatch: expected " + expected + " but received " + actual
                        + ". Cross-currency settlement requires an explicit exchange rate conversion.");
    }
}
