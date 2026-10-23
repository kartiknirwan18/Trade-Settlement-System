package com.example.settlement.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the identity performing the current operation. Falls back to
 * {@code system} for background jobs (e.g. the overdue scheduler) that run
 * outside an authenticated request.
 */
@Component
public class CurrentUserProvider {

    public static final String SYSTEM_USER = "system";

    public String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return SYSTEM_USER;
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return SYSTEM_USER;
        }
        return name;
    }
}
