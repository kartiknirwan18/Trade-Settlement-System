package com.example.settlement.dto.response;

import com.example.settlement.enums.Role;

import java.time.LocalDateTime;

/** Safe projection of a user; never exposes the password hash. */
public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        Role role,
        boolean enabled,
        LocalDateTime createdAt) {
}
