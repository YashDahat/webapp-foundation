package com.webappfoundation.dto;

import com.webappfoundation.model.Role;
import lombok.Data;

/** Admin changes a user's role. Assigning SUPER_ADMIN is restricted to a single holder. */
@Data
public class UpdateUserRoleRequest {
    private Role role;
}
