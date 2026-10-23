package com.example.settlement.dto.response;

import com.example.settlement.enums.Role;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        String username,
        Role role,
        String fullName) {
}
