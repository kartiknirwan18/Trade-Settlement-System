package com.example.settlement.exception;

/**
 * Thrown when a request is syntactically valid but violates a business rule
 * (e.g. invoicing a draft trade, overpaying an invoice). Maps to HTTP 400.
 */
public class BusinessRuleException extends RuntimeException {

    private final String errorCode;

    public BusinessRuleException(String message) {
        this("BUSINESS_RULE_VIOLATION", message);
    }

    public BusinessRuleException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
