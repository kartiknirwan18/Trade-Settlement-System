package com.example.settlement.controller;

import com.example.settlement.dto.common.ApiResponse;
import com.example.settlement.dto.request.LoginRequest;
import com.example.settlement.dto.request.RegisterUserRequest;
import com.example.settlement.dto.response.LoginResponse;
import com.example.settlement.dto.response.UserResponse;
import com.example.settlement.security.JwtService;
import com.example.settlement.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "JWT login and administrative user creation")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService, UserService userService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Authenticate and obtain a JWT",
            description = "Returns a bearer token to be sent in the Authorization header. Invalid credentials produce HTTP 401.")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username().trim().toLowerCase(java.util.Locale.ROOT),
                        request.password()));
        String username = authentication.getName();
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("AUDITOR");
        var user = userService.requireUser(username);
        String token = jwtService.generateToken(username, role);
        LoginResponse response = new LoginResponse(token, "Bearer", jwtService.expirationSeconds(),
                username, user.getRole(), user.getFullName());
        return ResponseEntity.ok(ApiResponse.ok("Authenticated", response));
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create an application user (ADMIN only)")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("User created", userService.register(request)));
    }
}
