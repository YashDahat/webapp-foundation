package com.webappfoundation.config;

import com.webappfoundation.security.JwtAuthFilter;
import com.webappfoundation.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    /** Comma-separated exact origin allowlist (e.g. the site's CloudFront domain). Empty = deny all cross-origin. */
    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, UserService userService, PasswordEncoder passwordEncoder) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/**", "/", "/index.html", "/static/**", "/*.js", "/*.css", "/*.json", "/*.ico", "/assets/**").permitAll()
                        // STAFF order-board allowlist — the ONLY /admin/** endpoints STAFF may hit: read the
                        // board/stream, read a single order + its stage history, and change ticket status
                        // (advance stage, block/resume). Everything else under /admin/** stays ADMIN-only.
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/orders/board", "/api/v1/admin/orders/board/stream").hasAnyAuthority("STAFF", "ADMIN", "SUPER_ADMIN")
                        // The All-orders archive is a literal that would otherwise be swallowed by the STAFF
                        // "/orders/*" rule below — pin it ADMIN-only FIRST so STAFF can't read the full record.
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/orders/archive").hasAnyAuthority("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/orders/*", "/api/v1/admin/orders/*/history").hasAnyAuthority("STAFF", "ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/orders/*/stage").hasAnyAuthority("STAFF", "ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/orders/*/block", "/api/v1/admin/orders/*/resume").hasAnyAuthority("STAFF", "ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN")
                        .anyRequest().permitAll()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(userService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);
        return authenticationProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Strict CORS allowlist. Only the exact origins in {@code app.cors.allowed-origins} may call the API
     * from a browser; an empty list means no cross-origin access. Never uses a "*" wildcard because the
     * client sends a bearer token (credentials). Dev defaults to localhost; prod must set the site origin.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = allowedOrigins == null || allowedOrigins.isBlank()
                ? List.of()
                : Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
