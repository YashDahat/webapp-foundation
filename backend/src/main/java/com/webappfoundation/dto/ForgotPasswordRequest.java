package com.webappfoundation.dto;

import lombok.Data;

/** Start a password reset: the account email a reset link should be sent to. */
@Data
public class ForgotPasswordRequest {
    private String email;
}
