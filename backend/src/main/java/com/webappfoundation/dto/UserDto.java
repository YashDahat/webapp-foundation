package com.webappfoundation.dto;

import com.webappfoundation.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Admin-facing view of a user (never exposes the password hash). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Integer id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private Role role;
    private boolean emailVerified;
    private boolean twoFactorEnabled;
}
