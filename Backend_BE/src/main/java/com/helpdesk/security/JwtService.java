package com.helpdesk.security;

import com.helpdesk.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and verifies HMAC-signed access tokens (JJWT picks HS256/384/512 from the
 * secret length). The token carries the user id and its
 * organization id; the filter re-checks both against the database on every request.
 */
@Service
public class JwtService {

    static final String ORG_CLAIM = "org";
    static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes long");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(expirationMinutes);
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(ORG_CLAIM, user.getOrganization().getId())
                .claim(ROLE_CLAIM, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public long getTtlSeconds() {
        return ttl.toSeconds();
    }

    /** Empty when the token is malformed, tampered with or expired. */
    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Object org = claims.get(ORG_CLAIM);
            if (claims.getSubject() == null || !(org instanceof Number)) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(Long.valueOf(claims.getSubject()), ((Number) org).longValue()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record TokenClaims(Long userId, Long organizationId) {
    }
}
