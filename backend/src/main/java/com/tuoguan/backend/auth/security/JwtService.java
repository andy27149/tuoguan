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
        return Jwts.builder()
                .subject(teacherId.toString())
                .claim("institutionId", institutionId.toString())
                .claim("role", role.name())
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
        Long institutionId = Long.valueOf(claims.get("institutionId", String.class));
        Role role = Role.valueOf(claims.get("role", String.class));
        return new JwtClaims(teacherId, institutionId, role);
    }
}
