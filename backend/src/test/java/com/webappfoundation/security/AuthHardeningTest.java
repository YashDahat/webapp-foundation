package com.webappfoundation.security;

import com.webappfoundation.model.Role;
import com.webappfoundation.model.User;
import com.webappfoundation.repository.UserRepository;
import com.webappfoundation.service.UserAdminService;
import com.webappfoundation.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auth-flow hardening checks (Security Hardening Plan, step 4):
 * T8 token revocation via tokenVersion, T7 reset-link single-use, T6 login lockout.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthHardeningTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserAdminService userAdminService;

    private User saveUser(String email, String phone) {
        return userRepository.save(User.builder()
                .firstName("Test").lastName("User").email(email).phone(phone)
                .password(passwordEncoder.encode("InitialPass123")).role(Role.USER)
                .emailVerified(true).build());
    }

    // ── T8 / finding #8 — bumping tokenVersion revokes an already-issued access token ──────────────
    @Test
    void accessToken_isRejectedAfterTokenVersionBump() throws Exception {
        User user = saveUser("revoke@test.com", "+919000000001");
        String token = jwtUtil.generateToken(user); // carries tv=0

        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        user.setTokenVersion(user.getTokenVersion() + 1); // simulate password change / logout-everywhere
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + token))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    assertThat(s).as("stale-version token must be rejected").isIn(401, 403);
                });
    }

    // ── T7 / finding #7 — a password-reset link is single-use ──────────────────────────────────────
    @Test
    void resetLink_isSingleUse() throws Exception {
        User user = saveUser("reset@test.com", "+919000000002");
        // Build a reset token exactly like AuthController.challengeToken(RESET): stage + current tv.
        String token = jwtUtil.generateChallengeToken(
                Map.of("stage", "reset", "tv", user.getTokenVersion()), user.getEmail());
        String body = "{ \"token\": \"" + token + "\", \"newPassword\": \"BrandNewPass9\" }";

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        // Second use of the same link is rejected (tokenVersion was bumped on the first reset).
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    // ── #8 follow-up — a role change bumps tokenVersion (revoking existing tokens) ─────────────────
    @Test
    void roleChange_bumpsTokenVersion() {
        User user = saveUser("rolechange@test.com", "+919000000003");
        long before = user.getTokenVersion();

        userAdminService.changeRole(user.getId(), Role.STAFF);

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getTokenVersion()).as("role change must bump tokenVersion").isGreaterThan(before);
        assertThat(reloaded.getRole()).isEqualTo(Role.STAFF);
    }

    // ── T6 / finding #6 — repeated login failures lock the account out (429) ───────────────────────
    @Test
    void login_locksOutAfterRepeatedFailures() throws Exception {
        String body = """
                { "username": "lockout@test.com", "password": "definitely-wrong-123" }
                """;
        // Default max-attempts is 5 → the first 5 are rejected as bad credentials (401)...
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        // ...the next attempt is locked out.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }
}
