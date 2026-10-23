package com.example.settlement.service.impl;

import com.example.settlement.audit.AuditActions;
import com.example.settlement.audit.AuditService;
import com.example.settlement.dto.request.RegisterUserRequest;
import com.example.settlement.dto.response.UserResponse;
import com.example.settlement.entity.User;
import com.example.settlement.enums.Role;
import com.example.settlement.exception.DuplicateResourceException;
import com.example.settlement.exception.ResourceNotFoundException;
import com.example.settlement.repository.UserRepository;
import com.example.settlement.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterUserRequest request) {
        User created = createUser(request.username(), request.email(), request.password(),
                request.fullName(), request.role());
        auditService.record("USER", created.getId(), AuditActions.USER_CREATED,
                Map.of("username", created.getUsername(), "role", created.getRole().name()),
                "User " + created.getUsername() + " created");
        return toResponse(created);
    }

    @Override
    @Transactional
    public User createUser(String username, String email, String rawPassword, String fullName, Role role) {
        String normalisedUsername = username.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsername(normalisedUsername)) {
            throw DuplicateResourceException.of("username", normalisedUsername);
        }
        if (userRepository.existsByEmail(email.trim().toLowerCase(Locale.ROOT))) {
            throw DuplicateResourceException.of("email", email);
        }
        User user = User.builder()
                .username(normalisedUsername)
                .email(email.trim().toLowerCase(Locale.ROOT))
                .password(passwordEncoder.encode(rawPassword))
                .fullName(fullName)
                .role(role)
                .enabled(true)
                .build();
        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(UserServiceImpl::toResponse).toList();
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getFullName(), user.getRole(), user.isEnabled(), user.getCreatedAt());
    }
}
