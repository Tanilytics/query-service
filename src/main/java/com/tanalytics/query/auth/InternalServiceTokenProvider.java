package com.tanalytics.query.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class InternalServiceTokenProvider {

    private final SecretKey signingKey;
    private final String internalAudience;
    private final String serviceName;
    private final long expirySeconds;

    public InternalServiceTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${auth.service.internal-audience:auth-internal}") String internalAudience,
            @Value("${auth.service.internal-service-name:query-service}") String serviceName,
            @Value("${auth.service.service-token-expiry-seconds:60}") long expirySeconds
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.internalAudience = internalAudience;
        this.serviceName = serviceName;
        this.expirySeconds = expirySeconds;
    }

    public String generateToken() {
        return Jwts.builder()
                .subject(serviceName)
                .claim("type", "service")
                .claim("service", serviceName)
                .audience().add(internalAudience).and()
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(expirySeconds)))
                .signWith(signingKey)
                .compact();
    }
}
