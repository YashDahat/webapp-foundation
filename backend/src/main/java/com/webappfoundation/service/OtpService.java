package com.webappfoundation.service;

import com.webappfoundation.model.User;
import com.webappfoundation.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Issues and verifies the 6-digit email login OTP for two-factor auth. The code is emailed to the user
 * and only its BCrypt hash is stored (never plaintext). A code expires after {@code ttl-minutes} and is
 * locked after {@code max-attempts} failed verifications; a fresh {@link #issue} resets the counter.
 */
@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.security.otp.ttl-minutes:10}")
    private long ttlMinutes;

    @Value("${app.security.otp.max-attempts:5}")
    private int maxAttempts;

    public OtpService(UserRepository userRepository, PasswordEncoder passwordEncoder, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /** Generate a fresh 6-digit code, store its hash + TTL on the user, and email it. */
    public void issue(User user) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        user.setOtpCodeHash(passwordEncoder.encode(code));
        user.setOtpExpiresAt(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES));
        user.setOtpAttempts(0);
        userRepository.save(user);
        emailService.sendHtml(user.getEmail(), "Your login verification code", otpHtml(code));
    }

    /**
     * Verify a submitted code. Returns true and clears the code on success; on failure increments the
     * attempt counter (locking the code once max-attempts is reached) and returns false. Expired,
     * already-used, or over-limit codes return false.
     */
    public boolean verify(User user, String code) {
        if (user.getOtpCodeHash() == null || user.getOtpExpiresAt() == null) {
            return false;
        }
        if (Instant.now().isAfter(user.getOtpExpiresAt()) || user.getOtpAttempts() >= maxAttempts) {
            return false;
        }
        if (code != null && passwordEncoder.matches(code, user.getOtpCodeHash())) {
            clear(user);
            return true;
        }
        user.setOtpAttempts(user.getOtpAttempts() + 1);
        userRepository.save(user);
        return false;
    }

    private void clear(User user) {
        user.setOtpCodeHash(null);
        user.setOtpExpiresAt(null);
        user.setOtpAttempts(0);
        userRepository.save(user);
    }

    private String otpHtml(String code) {
        return "<div style='font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#212121'>"
                + "<h2 style='color:#1A3A6D'>Your verification code</h2>"
                + "<p>Use this code to finish signing in. It expires in " + ttlMinutes + " minutes.</p>"
                + "<p style='font-size:32px;font-weight:bold;letter-spacing:6px'>" + code + "</p>"
                + "<hr style='border:none;border-top:1px solid #eee'>"
                + "<p style='color:#888;font-size:12px'>If you didn't try to sign in, you can ignore this email.</p>"
                + "</div>";
    }
}
