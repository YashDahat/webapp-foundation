package com.webappfoundation.service;

import com.webappfoundation.dto.BulkUploadResult;
import com.webappfoundation.dto.UserDto;
import com.webappfoundation.model.Role;
import com.webappfoundation.model.User;
import com.webappfoundation.repository.UserRepository;
import com.webappfoundation.security.PasswordPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Admin user management: list users, change roles (SUPER_ADMIN is a single-holder role), delete users
 * (SUPER_ADMIN only — enforced by the controller), and bulk-import from a spreadsheet. Never exposes the
 * password hash.
 */
@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SpreadsheetParser spreadsheetParser;

    public UserAdminService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            PasswordPolicy passwordPolicy, SpreadsheetParser spreadsheetParser) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.spreadsheetParser = spreadsheetParser;
    }

    public List<UserDto> list() {
        return userRepository.findAll().stream().map(this::toDto).toList();
    }

    /** Change a user's role. Assigning SUPER_ADMIN is rejected if another user already holds it. */
    @Transactional
    public UserDto changeRole(Integer id, Role role) {
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required.");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
        if (role == Role.SUPER_ADMIN && user.getRole() != Role.SUPER_ADMIN
                && userRepository.findAll().stream().anyMatch(u -> u.getRole() == Role.SUPER_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A super admin already exists — only one is allowed. Demote the current one first.");
        }
        user.setRole(role);
        return toDto(userRepository.save(user));
    }

    /** Delete a user. The acting user must be SUPER_ADMIN (checked by the controller); a SUPER_ADMIN cannot be deleted. */
    @Transactional
    public void delete(Integer id, Integer actingUserId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
        if (user.getId().equals(actingUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot delete your own account.");
        }
        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The super admin cannot be deleted.");
        }
        userRepository.delete(user);
    }

    /**
     * Bulk-import users from a .csv/.xlsx. Columns: firstName, lastName, email, phone, password, role
     * (role optional, defaults USER; SUPER_ADMIN is never assignable via import). Existing email/phone
     * rows are skipped. Bulk-created users are email-verified.
     */
    @Transactional
    public BulkUploadResult bulkImport(MultipartFile file) {
        BulkUploadResult result = BulkUploadResult.builder().build();
        List<Map<String, String>> rows = spreadsheetParser.parse(file);
        int rowNum = 1; // header is row 1; data starts at 2
        for (Map<String, String> row : rows) {
            rowNum++;
            String email = row.getOrDefault("email", "").trim();
            String phone = row.getOrDefault("phone", "").trim();
            String password = row.getOrDefault("password", "").trim();
            if (email.isEmpty() || phone.isEmpty() || password.isEmpty()) {
                result.addError(rowNum, "email, phone and password are required");
                continue;
            }
            if (userRepository.findByEmail(email).isPresent() || userRepository.findByPhone(phone).isPresent()) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            try {
                passwordPolicy.validate(password);
            } catch (ResponseStatusException e) {
                result.addError(rowNum, "weak password (min 8 chars incl. a letter and a digit)");
                continue;
            }
            Role role = Role.USER;
            String roleRaw = row.getOrDefault("role", "").trim().toUpperCase();
            if (roleRaw.equals("ADMIN")) {
                role = Role.ADMIN; // SUPER_ADMIN is intentionally never created via import
            } else if (roleRaw.equals("STAFF")) {
                role = Role.STAFF; // operational order-board staff
            }
            userRepository.save(User.builder()
                    .firstName(row.getOrDefault("firstname", "").trim())
                    .lastName(row.getOrDefault("lastname", "").trim())
                    .email(email)
                    .phone(phone)
                    .password(passwordEncoder.encode(password))
                    .role(role)
                    .emailVerified(true)
                    .build());
            result.setCreated(result.getCreated() + 1);
        }
        return result;
    }

    private UserDto toDto(User u) {
        return UserDto.builder()
                .id(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .email(u.getEmail())
                .phone(u.getPhone())
                .role(u.getRole())
                .emailVerified(u.isEmailVerified())
                .twoFactorEnabled(u.isTwoFactorEnabled())
                .build();
    }
}
