package com.example.settlement.security;

import com.example.settlement.config.SettlementProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and validates HS256 JSON Web Tokens.
 *
 * <p>The signing secret is read from the {@code JWT_SECRET} environment variable and
 * must be at least 32 bytes. No default secret is shipped for non-development
 * profiles - the application refuses to start without one.</p>
 */
@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int MIN_SECRET_BYTES = 32;

    private final SettlementProperties properties;
    private SecretKey signingKey;

    public JwtService(SettlementProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialiseKey() {
        String secret = properties.getSecurity().getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret is not configured. Set the JWT_SECRET environment variable to a random value of at least 32 characters.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT secret must be at least " + MIN_SECRET_BYTES + " bytes (256 bits) for HS256; configured length was " + bytes.length);
        }
        this.signingKey = Keys.hmacShaKeyFor(bytes);
        log.info("JWT signing key initialised (issuer={}, expiration={}m)",
                properties.getSecurity().getJwt().getIssuer(),
                properties.getSecurity().getJwt().getExpirationMinutes());
    }

    public String generateToken(String username, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationSeconds());
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuer(properties.getSecurity().getJwt().getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /** Parses and verifies the token; throws {@link JwtException} when it is invalid or expired. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.getSecurity().getJwt().getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return false;
        }
    }

    public long expirationSeconds() {
        return properties.getSecurity().getJwt().getExpirationMinutes() * 60L;
    }
}
