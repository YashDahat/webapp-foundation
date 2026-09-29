package com.webappfoundation.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Minimum password-strength policy enforced on self-service register (and any password change):
 * at least 8 characters, including at least one letter and one digit. Throws a 400 on a weak password.
 * BCrypt still hashes the stored value (see PasswordEncoderConfig).
 */
@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 8;

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
    }
}
