package com.example.settlement.dto.common;

import java.time.LocalDateTime;

/**
 * Consistent success envelope returned by every REST endpoint.
 *
 * @param success   always {@code true} for a successful response
 * @param message   short human readable outcome
 * @param data      payload (entity DTO, page of DTOs, or dashboard aggregate)
 * @param timestamp server time the response was produced
 */
public record ApiResponse<T>(boolean success, String message, T data, LocalDateTime timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", data, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, LocalDateTime.now());
    }
}
