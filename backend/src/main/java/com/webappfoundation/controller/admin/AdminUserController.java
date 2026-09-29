package com.webappfoundation.controller.admin;

import com.webappfoundation.dto.BulkUploadResult;
import com.webappfoundation.dto.UpdateUserRoleRequest;
import com.webappfoundation.dto.UserDto;
import com.webappfoundation.model.Role;
import com.webappfoundation.model.User;
import com.webappfoundation.repository.UserRepository;
import com.webappfoundation.security.CurrentUser;
import com.webappfoundation.service.UserAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Admin user management. ADMIN + SUPER_ADMIN can list users, change roles, and bulk-import; deleting a
 * user is restricted to SUPER_ADMIN (enforced here). ADMIN-gated at the filter level by SecurityConfig
 * ({@code /api/v1/admin/**} → hasAnyAuthority(ADMIN, SUPER_ADMIN)).
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserAdminService userAdminService;
    private final UserRepository userRepository;

    public AdminUserController(UserAdminService userAdminService, UserRepository userRepository) {
        this.userAdminService = userAdminService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<UserDto> list() {
        return userAdminService.list();
    }

    @PutMapping("/{id}/role")
    public UserDto changeRole(@PathVariable Integer id, @RequestBody UpdateUserRoleRequest request) {
        return userAdminService.changeRole(id, request.getRole());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser Integer userId, @PathVariable Integer id) {
        requireSuperAdmin(userId);
        userAdminService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/bulk-upload", consumes = "multipart/form-data")
    public BulkUploadResult bulkUpload(@RequestParam("file") MultipartFile file) {
        return userAdminService.bulkImport(file);
    }

    private void requireSuperAdmin(Integer userId) {
        Role role = userId == null ? null
                : userRepository.findById(userId).map(User::getRole).orElse(null);
        if (role != Role.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the super admin can delete users.");
        }
    }
}
