package com.webappfoundation.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-account login throttle: after {@code app.security.login.max-attempts} consecutive
 * failures a key is locked out for {@code app.security.login.lockout-minutes}. Single EC2 per site,
 * so a local map is sufficient (no distributed store); AWS WAF absorbs volumetric/IP-level abuse.
 * Call {@link #checkNotLocked} before authenticating, {@link #recordFailure} on failure, and
 * {@link #reset} on success.
 */
@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final long lockoutMs;
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(@Value("${app.security.login.max-attempts:5}") int maxAttempts,
                            @Value("${app.security.login.lockout-minutes:15}") long lockoutMinutes) {
        this.maxAttempts = maxAttempts;
        this.lockoutMs = lockoutMinutes * 60_000L;
    }

    /** Rejects with 429 when the key is currently locked out. */
    public void checkNotLocked(String key) {
        Attempt a = attempts.get(norm(key));
        if (a != null && a.lockedUntil > System.currentTimeMillis()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Please try again later.");
        }
    }

    /** Records a failed attempt; locks the key once max-attempts is reached. */
    public void recordFailure(String key) {
        Attempt a = attempts.computeIfAbsent(norm(key), k -> new Attempt());
        synchronized (a) {
            long now = System.currentTimeMillis();
            if (a.lockedUntil != 0 && a.lockedUntil <= now) { // a prior lockout has elapsed — start fresh
                a.count = 0;
                a.lockedUntil = 0;
            }
            a.count++;
            if (a.count >= maxAttempts) {
                a.lockedUntil = now + lockoutMs;
            }
        }
    }

    /** Clears any failure state for the key (call on a successful login). */
    public void reset(String key) {
        attempts.remove(norm(key));
    }

    private static String norm(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }

    private static final class Attempt {
        int count;
        long lockedUntil;
    }
}
