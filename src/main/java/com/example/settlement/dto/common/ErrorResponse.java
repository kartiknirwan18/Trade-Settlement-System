package com.example.settlement.dto.common;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Consistent error envelope returned by the global exception handler.
 *
 * @param success   always {@code false}
 * @param errorCode stable machine readable code (e.g. {@code VALIDATION_FAILED})
 * @param message   human readable explanation
 * @param details   per-field validation problems, empty when not applicable
 * @param path      request path that produced the error
 * @param timestamp server time the response was produced
 */
public record ErrorResponse(
        boolean success,
        String errorCode,
        String message,
        List<Violation> details,
        String path,
        LocalDateTime timestamp) {

    public record Violation(String field, String message) {
    }

    public static ErrorResponse of(String errorCode, String message, String path) {
        return new ErrorResponse(false, errorCode, message, List.of(), path, LocalDateTime.now());
    }

    public static ErrorResponse of(String errorCode, String message, List<Violation> details, String path) {
        return new ErrorResponse(false, errorCode, message, details == null ? List.of() : details, path, LocalDateTime.now());
    }
}
