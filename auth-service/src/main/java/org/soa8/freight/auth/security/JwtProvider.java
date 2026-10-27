package org.soa8.freight.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final String issuer;
    private final String audience;
    private final long expirationMillis;

    public JwtProvider(
            @Value("${jwt.secret:soa-team8-default-secret-key-must-be-at-least-256-bits-long-for-hmac-sha256!}") String secret,
            @Value("${jwt.issuer:soa-team8-auth-service}") String issuer,
            @Value("${jwt.audience:soa-team8-platform}") String audience,
            @Value("${jwt.expiration-seconds:3600}") long expirationSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.expirationMillis = expirationSeconds * 1000;
    }

    public String generateToken(String userId, String username, Set<String> roles) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("username", username)
                .claim("roles", List.copyOf(roles))
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMillis))
                .signWith(key)
                .compact();
    }

    public Claims validateAndGetClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
