package com.example.settlement.enums;

/** Business kind of an overdue alert. */
public enum AlertType {
    /** The due date has passed and a positive outstanding balance remains. */
    OVERDUE_INVOICE,
    /** The due date is approaching and the invoice is not fully settled. */
    UPCOMING_DUE
}
