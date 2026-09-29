package com.webappfoundation.dto;

import lombok.Data;

/** Authenticated password change: verify the current password, then set a new one (PasswordPolicy applies). */
@Data
public class ChangePasswordRequest {
    private String currentPassword;
    private String newPassword;
}
