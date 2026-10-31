package com.webappfoundation.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Minimum password-strength policy enforced on self-service register (and any password change):
 * at least 10 characters, including at least one letter and one digit, and not a well-known common
 * password. Throws a 400 on a weak password. BCrypt still hashes the stored value (see
 * PasswordEncoderConfig).
 */
@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 10;

    /** A small denylist of the most common/guessable passwords (compared case-insensitively). */
    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password123", "passw0rd", "1234567890", "12345678",
            "123456789", "qwerty123", "qwertyuiop", "admin123", "changeme123", "letmein123",
            "welcome123", "iloveyou1", "abc123456", "football1");

    public void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least " + MIN_LENGTH + " characters.");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must include at least one letter and one digit.");
        }
        if (COMMON.contains(password.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password is too common; choose a less guessable password.");
        }
    }
}
