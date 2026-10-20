package com.webappfoundation.controller;

import com.webappfoundation.dto.AuthRequest;
import com.webappfoundation.dto.AuthResponse;
import com.webappfoundation.dto.ChangePasswordRequest;
import com.webappfoundation.dto.ForgotPasswordRequest;
import com.webappfoundation.dto.LoginResponse;
import com.webappfoundation.dto.RegisterRequest;
import com.webappfoundation.dto.ResetPasswordRequest;
import com.webappfoundation.dto.VerifyTwoFactorRequest;
import com.webappfoundation.model.Role;
import com.webappfoundation.model.User;
import com.webappfoundation.repository.UserRepository;
import com.webappfoundation.security.CurrentUser;
import com.webappfoundation.security.PasswordPolicy;
import com.webappfoundation.service.AuditActions;
import com.webappfoundation.service.AuditService;
import com.webappfoundation.service.EmailService;
import com.webappfoundation.service.OtpService;
import com.webappfoundation.service.UserService;
import com.webappfoundation.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String TWO_FACTOR_STAGE = "2fa";
    private static final String VERIFY_STAGE = "verify";   // signup email verification
    private static final String RESET_STAGE = "reset";     // forgot-password reset link

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final PasswordPolicy passwordPolicy;
    private final EmailService emailService;
    private final AuditService auditService;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil, UserService userService,
                          UserRepository userRepository, PasswordEncoder passwordEncoder, OtpService otpService,
                          PasswordPolicy passwordPolicy, EmailService emailService, AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.passwordPolicy = passwordPolicy;
        this.emailService = emailService;
        this.auditService = auditService;
    }

    /**
     * Password login. Blocks unverified accounts (re-emails a signup OTP and returns
     * {@code verificationRequired}); for verified users with 2FA on, emails an OTP and returns
     * {@code twoFactorRequired}; otherwise returns the JWT.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody AuthRequest authRequest) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getUsername(), authRequest.getPassword())
            );
        } catch (RuntimeException e) {
            auditService.recordActor(null, authRequest.getUsername(), null, AuditActions.LOGIN_FAILURE,
                    AuditActions.FAILURE, "USER", authRequest.getUsername(), "bad credentials");
            throw e;
        }
        final User user = (User) userService.loadUserByUsername(authRequest.getUsername());

        if (!user.isEmailVerified()) {
            otpService.issue(user);
            return ResponseEntity.ok(LoginResponse.builder()
                    .verificationRequired(true)
                    .challengeId(challengeToken(user, VERIFY_STAGE))
                    .build());
        }
        if (user.isTwoFactorEnabled()) {
            otpService.issue(user);
            return ResponseEntity.ok(LoginResponse.builder()
                    .twoFactorRequired(true)
                    .challengeId(challengeToken(user, TWO_FACTOR_STAGE))
                    .build());
        }
        auditService.recordActor(user.getId(), user.getEmail(), roleName(user), AuditActions.LOGIN_SUCCESS,
                AuditActions.SUCCESS, "USER", String.valueOf(user.getId()), null);
        return ResponseEntity.ok(LoginResponse.builder().token(jwtUtil.generateToken(user)).build());
    }

    /** Second step of 2FA login: verify the emailed code against the challenge and issue the JWT. */
    @PostMapping("/verify-2fa")
    public ResponseEntity<AuthResponse> verifyTwoFactor(@RequestBody VerifyTwoFactorRequest request) {
        User user = (User) userService.loadUserByUsername(challengeUsername(request.getChallengeId(), TWO_FACTOR_STAGE));
        if (!otpService.verify(user, request.getCode())) {
            auditService.recordActor(user.getId(), user.getEmail(), roleName(user), AuditActions.LOGIN_FAILURE,
                    AuditActions.FAILURE, "USER", String.valueOf(user.getId()), "bad 2FA code");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired verification code.");
        }
        auditService.recordActor(user.getId(), user.getEmail(), roleName(user), AuditActions.TWO_FACTOR_VERIFIED,
                AuditActions.SUCCESS, "USER", String.valueOf(user.getId()), null);
        return ResponseEntity.ok(new AuthResponse(jwtUtil.generateToken(user)));
    }

    /** Second step of signup: verify the emailed code, mark the account verified, and issue the JWT. */
    @PostMapping("/verify-signup")
    public ResponseEntity<AuthResponse> verifySignup(@RequestBody VerifyTwoFactorRequest request) {
        User user = (User) userService.loadUserByUsername(challengeUsername(request.getChallengeId(), VERIFY_STAGE));
        if (!otpService.verify(user, request.getCode())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired verification code.");
        }
        user.setEmailVerified(true);
        userRepository.save(user);
        auditService.recordActor(user.getId(), user.getEmail(), roleName(user), AuditActions.SIGNUP_VERIFIED,
                AuditActions.SUCCESS, "USER", String.valueOf(user.getId()), null);
        return ResponseEntity.ok(new AuthResponse(jwtUtil.generateToken(user)));
    }

    /** Enable email-OTP 2FA for the current user. */
    @PostMapping("/2fa/enable")
    public ResponseEntity<Void> enableTwoFactor(@CurrentUser Integer userId) {
        setTwoFactor(userId, true);
        return ResponseEntity.ok().build();
    }

    /** Disable email-OTP 2FA for the current user. */
    @PostMapping("/2fa/disable")
    public ResponseEntity<Void> disableTwoFactor(@CurrentUser Integer userId) {
        setTwoFactor(userId, false);
        return ResponseEntity.ok().build();
    }

    /**
     * Self-service signup. Always creates a USER (admin stays seed-only). The account is created
     * UNVERIFIED and a 6-digit OTP is emailed — no JWT is issued yet; the response carries
     * {@code verificationRequired=true} + a {@code challengeId}. Finish via /verify-signup. Rejects
     * duplicate email/phone (409) and weak passwords (400, {@link PasswordPolicy}).
     */
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@RequestBody RegisterRequest request) {
        passwordPolicy.validate(request.getPassword());
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        if (userRepository.findByPhone(request.getPhone()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number already registered");
        }
        final User user = userRepository.save(User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .emailVerified(false)
                .build());
        otpService.issue(user);
        auditService.recordActor(user.getId(), user.getEmail(), roleName(user), AuditActions.REGISTER,
                AuditActions.SUCCESS, "USER", String.valueOf(user.getId()), "awaiting email verification");
        return ResponseEntity.status(HttpStatus.CREATED).body(LoginResponse.builder()
                .verificationRequired(true)
                .challengeId(challengeToken(user, VERIFY_STAGE))
                .build());
    }

    /** Authenticated password change: verify the current password, then set a new one. */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@CurrentUser Integer userId, @RequestBody ChangePasswordRequest request) {
        User user = requireCurrentUser(userId);
        if (request.getCurrentPassword() == null
                || !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }
        passwordPolicy.validate(request.getNewPassword());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        auditService.record(AuditActions.PASSWORD_CHANGED, AuditActions.SUCCESS, "USER", String.valueOf(user.getId()), null);
        return ResponseEntity.ok().build();
    }

    /**
     * Start a password reset. Always returns 200 (never reveals whether the email exists); when it does,
     * emails a link {@code <app.base-url>/reset-password?token=<signed reset token>}.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            userRepository.findByEmail(request.getEmail().trim()).ifPresent(user -> {
                String token = challengeToken(user, RESET_STAGE);
                String link = appBaseUrl + "/reset-password?token=" + token;
                emailService.sendHtml(user.getEmail(), "Reset your password", resetHtml(link));
                auditService.recordActor(user.getId(), user.getEmail(), roleName(user),
                        AuditActions.PASSWORD_RESET_REQUESTED, AuditActions.SUCCESS, "USER", String.valueOf(user.getId()), null);
            });
        }
        return ResponseEntity.ok().build();
    }

    /** Complete a password reset using the token from the emailed link. */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        User user = (User) userService.loadUserByUsername(challengeUsername(request.getToken(), RESET_STAGE));
        passwordPolicy.validate(request.getNewPassword());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        auditService.recordActor(user.getId(), user.getEmail(), roleName(user), AuditActions.PASSWORD_RESET,
                AuditActions.SUCCESS, "USER", String.valueOf(user.getId()), null);
        return ResponseEntity.ok().build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** A short-lived signed challenge/reset token carrying a stage claim + the user's username (subject). */
    private String challengeToken(User user, String stage) {
        return jwtUtil.generateToken(Map.of("stage", stage), user.getUsername());
    }

    /** Validate a challenge/reset token for the expected stage and return its subject (username). */
    private String challengeUsername(String token, String expectedStage) {
        try {
            if (!expectedStage.equals(jwtUtil.extractClaim(token, c -> c.get("stage", String.class)))
                    || jwtUtil.extractExpiration(token).before(new Date())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired link.");
            }
            return jwtUtil.extractUsername(token);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired link.");
        }
    }

    private User requireCurrentUser(Integer userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in.");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in."));
    }

    private void setTwoFactor(Integer userId, boolean enabled) {
        User user = requireCurrentUser(userId);
        user.setTwoFactorEnabled(enabled);
        if (!enabled) {
            user.setOtpCodeHash(null);
            user.setOtpExpiresAt(null);
            user.setOtpAttempts(0);
        }
        userRepository.save(user);
        auditService.record(enabled ? AuditActions.TWO_FACTOR_ENABLED : AuditActions.TWO_FACTOR_DISABLED,
                AuditActions.SUCCESS, "USER", String.valueOf(user.getId()), null);
    }

    private static String roleName(User user) {
        return user.getRole() == null ? null : user.getRole().name();
    }

    private String resetHtml(String link) {
        return "<div style='font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#212121'>"
                + "<h2 style='color:#1A3A6D'>Reset your password</h2>"
                + "<p>We received a request to reset your password. Click the button below to choose a new one. "
                + "This link expires soon; if you didn't request it, you can ignore this email.</p>"
                + "<p><a href='" + link + "' style='display:inline-block;background:#1A3A6D;color:#fff;"
                + "padding:10px 18px;border-radius:6px;text-decoration:none'>Reset password</a></p>"
                + "<p style='color:#888;font-size:12px'>Or paste this link into your browser:<br>" + link + "</p>"
                + "</div>";
    }
}
