package org.soa8.freight.declaration.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component("declarationTokenValidator")
public class TokenValidator {

    private final SecretKey key;
    private final String issuer;
    private final String audience;

    public TokenValidator(
            @Value("${jwt.secret:soa-team8-default-secret-key-must-be-at-least-256-bits-long-for-hmac-sha256!}") String secret,
            @Value("${jwt.issuer:soa-team8-auth-service}") String issuer,
            @Value("${jwt.audience:soa-team8-platform}") String audience) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
    }

    public Claims validateToken(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new SecurityException("Missing or malformed Authorization header");
        }
        String token = bearerToken.substring(7).trim();
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean hasRole(Claims claims, String expectedRole) {
        List<?> roles = claims.get("roles", List.class);
        if (roles == null) return false;
        return roles.stream().anyMatch(r -> expectedRole.equalsIgnoreCase(String.valueOf(r)));
    }
}
