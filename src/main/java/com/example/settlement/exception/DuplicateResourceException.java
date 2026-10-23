package com.example.settlement.exception;

/** Thrown when a unique business key (reference, invoice number) already exists. Maps to HTTP 409. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    public static DuplicateResourceException of(String field, Object value) {
        return new DuplicateResourceException(field + " already exists: " + value);
    }
}
