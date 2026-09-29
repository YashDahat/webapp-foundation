package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for POST /api/v1/auth/login and /register. Discriminated by two flags:
 * - Logged in         → {@code token} set (both flags false).
 * - 2FA required       → {@code twoFactorRequired=true}, {@code challengeId} set, {@code token} null.
 *   Complete via POST {challengeId, code} to /api/v1/auth/verify-2fa.
 * - Email unverified   → {@code verificationRequired=true}, {@code challengeId} set, {@code token} null.
 *   Complete via POST {challengeId, code} to /api/v1/auth/verify-signup (OTP was emailed).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private boolean twoFactorRequired;
    private boolean verificationRequired;
    private String challengeId;
}
