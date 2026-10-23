package com.example.settlement.enums;

/** Application roles used for role-based authorization. */
public enum Role {
    /** Configuration and user management. */
    ADMIN,
    /** Invoices, payments and reconciliation. */
    FINANCE,
    /** Trade creation and viewing. */
    TRADER,
    /** Read-only access to audit and financial records. */
    AUDITOR
}
