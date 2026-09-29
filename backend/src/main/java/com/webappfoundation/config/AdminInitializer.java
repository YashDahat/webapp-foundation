package com.webappfoundation.config;

import com.webappfoundation.model.Role;
import com.webappfoundation.model.User;
import com.webappfoundation.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the single SUPER_ADMIN user from the admin.email / admin.password properties (which carry
 * demo-safe defaults in application.properties). Idempotent — never creates a duplicate; if the seed
 * user already exists it is upgraded to SUPER_ADMIN + email-verified so the owner can always log in and
 * delete users. This is the ONLY user-seeding component; a business DataSeeder must seed domain content only.
 */
@Component
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:owner@yourbusiness.com}")
    private String adminEmail;

    @Value("${admin.phone:+910000000000}")
    private String adminPhone;

    @Value("${admin.password:changeme123}")
    private String adminPassword;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        User admin = userRepository.findByEmail(adminEmail).orElse(null);
        if (admin == null) {
            userRepository.save(User.builder()
                    .firstName("Admin")
                    .lastName("User")
                    .email(adminEmail)
                    .phone(adminPhone)
                    .password(passwordEncoder.encode(adminPassword))
                    .role(Role.SUPER_ADMIN)
                    .emailVerified(true)
                    .build());
        } else if (admin.getRole() != Role.SUPER_ADMIN || !admin.isEmailVerified()) {
            // Upgrade an existing seed row (e.g. created before these fields) so the owner keeps full access.
            admin.setRole(Role.SUPER_ADMIN);
            admin.setEmailVerified(true);
            userRepository.save(admin);
        }
    }
}
