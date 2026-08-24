package com.tuoguan.backend.auth.security;

import com.tuoguan.backend.auth.domain.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-jwt-secret-key-0123456789ABCDEF";

    private final JwtService jwtService = new JwtService(SECRET);

    @Test
    void issueThenParseRoundTripsClaims() {
        String token = jwtService.issueToken(42L, 7L, Role.ADMIN);

        JwtClaims claims = jwtService.parseToken(token);

        assertThat(claims.teacherId()).isEqualTo(42L);
        assertThat(claims.institutionId()).isEqualTo(7L);
        assertThat(claims.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void issueThenParseRoundTripsClaimsWithNullInstitutionId() {
        String token = jwtService.issueToken(1L, null, Role.PLATFORM_ADMIN);

        JwtClaims claims = jwtService.parseToken(token);

        assertThat(claims.teacherId()).isEqualTo(1L);
        assertThat(claims.institutionId()).isNull();
        assertThat(claims.role()).isEqualTo(Role.PLATFORM_ADMIN);
    }

    @Test
    void parseTokenRejectsExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        Instant past = Instant.now().minusSeconds(3600);
        String expiredToken = Jwts.builder()
                .subject("1")
                .claim("institutionId", "1")
                .claim("role", Role.TEACHER.name())
                .issuedAt(Date.from(past.minusSeconds(60)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> jwtService.parseToken(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parseTokenRejectsTamperedSignature() {
        String token = jwtService.issueToken(1L, 1L, Role.TEACHER);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.parseToken(tampered))
                .isInstanceOf(JwtException.class);
    }
}
