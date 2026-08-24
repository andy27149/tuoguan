package com.tuoguan.backend.auth.security;

import com.tuoguan.backend.auth.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public class JwtService {

    private static final Duration TOKEN_TTL = Duration.ofDays(30);

    private final SecretKey key;

    public JwtService(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String issueToken(Long teacherId, Long institutionId, Role role) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(teacherId.toString())
                .claim("role", role.name());
        if (institutionId != null) {
            builder.claim("institutionId", institutionId.toString());
        }
        return builder
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TOKEN_TTL)))
                .signWith(key)
                .compact();
    }

    public JwtClaims parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long teacherId = Long.valueOf(claims.getSubject());
        String institutionIdClaim = claims.get("institutionId", String.class);
        Long institutionId = institutionIdClaim == null ? null : Long.valueOf(institutionIdClaim);
        Role role = Role.valueOf(claims.get("role", String.class));
        return new JwtClaims(teacherId, institutionId, role);
    }
}
