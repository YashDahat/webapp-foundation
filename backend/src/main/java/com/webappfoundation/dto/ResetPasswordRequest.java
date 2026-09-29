package com.webappfoundation.dto;

import lombok.Data;

/** Complete a password reset: the token from the emailed link plus the new password (PasswordPolicy applies). */
@Data
public class ResetPasswordRequest {
    private String token;
    private String newPassword;
}
