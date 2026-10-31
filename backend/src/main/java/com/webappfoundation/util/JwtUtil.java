package com.webappfoundation.util;

import com.webappfoundation.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    /** Short TTL for challenge / password-reset tokens (default 15 min) — much shorter than access tokens. */
    @Value("${jwt.challenge-expiration-ms:900000}")
    private long challengeExpirationMs;

    /**
     * Fail fast at startup rather than run with a missing or weak signing key. An HS256 key must be
     * at least 256 bits (32 bytes); a blank/short secret means either JWT_SECRET is unset in prod or
     * a trivially forgeable key is in use — refuse to boot so forged admin tokens are impossible.
     */
    @PostConstruct
    void validateSigningKey() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "jwt.secret (env JWT_SECRET) must be set to at least 32 bytes. Refusing to start "
                            + "with a missing or weak JWT signing key.");
        }
    }

    public String generateToken(UserDetails userDetails) {
        // Carry the role as a claim so the frontend can gate UI (backend authz still
        // re-loads authorities from the DB). Authority is the bare role name (no ROLE_ prefix).
        Map<String, Object> claims = new HashMap<>();
        userDetails.getAuthorities().stream().findFirst()
                .ifPresent(authority -> claims.put("role", authority.getAuthority()));
        // Bind the token to the user's current credential version so a later bump revokes it.
        if (userDetails instanceof User user) {
            claims.put("tv", user.getTokenVersion());
        }
        return generateToken(claims, userDetails.getUsername(), expirationMs);
    }

    public String generateToken(Map<String, Object> extraClaims, String subject) {
        return generateToken(extraClaims, subject, expirationMs);
    }

    /** Short-lived token for email challenges / password-reset links (see {@code jwt.challenge-expiration-ms}). */
    public String generateChallengeToken(Map<String, Object> extraClaims, String subject) {
        return generateToken(extraClaims, subject, challengeExpirationMs);
    }

    public String generateToken(Map<String, Object> extraClaims, String subject, long ttlMs) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        return extractUsername(token).equals(userDetails.getUsername())
                && !extractExpiration(token).before(new Date())
                && isTokenVersionCurrent(token, userDetails);
    }

    /** The credential version claim ({@code tv}); null for legacy tokens issued before versioning. */
    public Long extractTokenVersion(String token) {
        Object tv = extractClaim(token, claims -> claims.get("tv"));
        return tv instanceof Number n ? n.longValue() : null;
    }

    /**
     * True when the token's credential version matches the user's current one (so a password
     * change/reset that bumps {@code tokenVersion} invalidates every earlier token). A legacy token
     * with no {@code tv} claim is treated as version 0.
     */
    public boolean isTokenVersionCurrent(String token, UserDetails userDetails) {
        if (!(userDetails instanceof User user)) {
            return true; // not a versioned principal
        }
        Long tv = extractTokenVersion(token);
        return (tv == null ? 0L : tv) == user.getTokenVersion();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
