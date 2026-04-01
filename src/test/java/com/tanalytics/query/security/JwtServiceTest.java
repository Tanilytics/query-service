package com.tanalytics.query.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "this-is-a-test-secret-with-at-least-32-bytes!!";

    @Test
    void parsesRoleAndSiteIdsFromAccessToken() {
        JwtService service = new JwtService(SECRET);
        String token = token("user-1", "access", "admin", List.of("site-a", "site-b"));

        assertTrue(service.isTokenValid(token));
        assertTrue(service.isAccessToken(token));
        assertEquals("admin", service.extractRole(token).orElseThrow());
        assertEquals(List.of("site-a", "site-b"), service.extractSiteIds(token));
        assertEquals("user-1", service.extractSubject(token));
    }

    @Test
    void rejectsRefreshTokenForAccessCheck() {
        JwtService service = new JwtService(SECRET);
        String refreshToken = token("user-1", "refresh", "admin", List.of("site-a"));

        assertTrue(service.isTokenValid(refreshToken));
        assertFalse(service.isAccessToken(refreshToken));
    }

    private String token(String subject, String type, String role, List<String> siteIds) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .claim("type", type)
                .claim("role", role)
                .claim("siteIds", siteIds)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }
}
