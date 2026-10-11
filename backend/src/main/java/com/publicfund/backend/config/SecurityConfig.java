package com.publicfund.backend.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.publicfund.backend.security.JwtAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter) {

        this.jwtAuthenticationFilter =
                jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of(
                        "http://localhost:5173", "http://127.0.0.1:5173"
                )
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of("*")
        );

        configuration.setExposedHeaders(
                List.of(
                        "Authorization"
                )
        );

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http)
            throws Exception {

        http

            .cors(cors ->
                    cors.configurationSource(
                            corsConfigurationSource()
                    )
            )

            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401); response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Your session has expired. Please log in again.\"}");
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403); response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"You do not have permission to perform this action.\"}");
                }))
            .csrf(csrf ->
                    csrf.disable()
            )

            .sessionManagement(session ->
                    session.sessionCreationPolicy(
                            SessionCreationPolicy.STATELESS
                    )
            )

            .authorizeHttpRequests(auth -> auth

                // Container error dispatches must preserve the original 404/500.
                // Requiring a new authentication here masks page/API failures as
                // session expiry in this stateless application. Normal requests,
                // including direct requests to /error, still use the rules below.
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR)
                .permitAll()

                // ==================================================
                // CORS
                // ==================================================

                .requestMatchers(
                        HttpMethod.OPTIONS,
                        "/**"
                )
                .permitAll()


                // ==================================================
                // Financial reconciliation reuses project ownership checks and existing roles.
                // Match these before broad /projects/** rules.
                // STAFF PROVISIONING (never public)
                // ==================================================

                .requestMatchers(HttpMethod.GET, "/api/projects/*/reconciliation", "/api/projects/*/reconciliation/*/file")
                .hasAnyRole("SERVICE_PROVIDER", "ADMINISTRATOR")
                .requestMatchers(HttpMethod.POST, "/api/projects/*/reconciliation/declarations", "/api/projects/*/reconciliation/returns")
                .hasRole("SERVICE_PROVIDER")
                .requestMatchers(HttpMethod.POST, "/api/projects/*/reconciliation/*/review")
                .hasRole("ADMINISTRATOR")

                .requestMatchers("/api/admin/users", "/api/admin/users/**")
                .hasRole("ADMINISTRATOR")

                .requestMatchers(HttpMethod.POST, "/api/auth/change-password")
                .hasRole("PASSWORD_CHANGE_REQUIRED")

                .requestMatchers("/api/auth/staff", "/api/auth/staff/**")
                .hasRole("ADMINISTRATOR")

                // ==================================================
                // PUBLIC AUTH
                // ==================================================

                .requestMatchers(
                        "/api/auth/register",
                        "/api/auth/google/config",
                        "/api/auth/google/challenge",
                        "/api/auth/google/login",
                        "/api/auth/verify-email",
                        "/api/auth/resend-verification",
                        "/api/auth/login",
                        "/api/auth/admin-login",
                        "/api/auth/forgot-password",
                        "/api/auth/reset-password"
                )
                .permitAll()


                // ==================================================
                // TEST ENDPOINTS
                // ==================================================

                .requestMatchers(
                        "/api/test/admin"
                )
                .hasRole("ADMINISTRATOR")

                .requestMatchers(
                        "/api/test/provider"
                )
                .hasRole("SERVICE_PROVIDER")

                .requestMatchers(
                        "/api/test/officer"
                )
                .hasRole("VERIFICATION_OFFICER")


                // ==================================================
                // FUNDING APPLICATIONS
                // ==================================================

                .requestMatchers(
                        HttpMethod.POST,
                        "/api/applications"
                )
                .hasRole("SERVICE_PROVIDER")

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/applications/my"
                )
                .hasRole("SERVICE_PROVIDER")

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/applications",
                        "/api/applications/**"
                )
                .hasRole("ADMINISTRATOR")

                .requestMatchers(
                        HttpMethod.POST,
                        "/api/applications/*/approve",
                        "/api/applications/*/reject"
                )
                .hasRole("ADMINISTRATOR")


                // ==================================================
                // PROJECTS - SERVICE PROVIDER
                // ==================================================
                .requestMatchers(HttpMethod.GET, "/api/projects/*/activity", "/api/projects/*/summary",
                        "/api/disbursements/*/proof")
                .hasAnyRole("SERVICE_PROVIDER", "ADMINISTRATOR")


                .requestMatchers(
                        HttpMethod.GET,
                        "/api/projects/my"
                )
                .hasRole("SERVICE_PROVIDER")

                .requestMatchers(
                        HttpMethod.POST,
                        "/api/projects/*/request-completion"
                )
                .hasRole("SERVICE_PROVIDER")


                // ==================================================
                // PROJECTS - ADMIN
                // ==================================================

                .requestMatchers(
                        HttpMethod.POST,
                        "/api/projects/from-application/*"
                )
                .hasRole("ADMINISTRATOR")

                .requestMatchers(
                        HttpMethod.POST,
                        "/api/projects/*/complete", "/api/projects/*/closeout-review"
                )
                .hasRole("ADMINISTRATOR")

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/projects",
                        "/api/projects/**"
                )
                .hasRole("ADMINISTRATOR")


                // ==================================================
                // PROJECT PHASES - SERVICE PROVIDER
                // ==================================================

                .requestMatchers(
                        HttpMethod.POST,
                        "/api/project-phases/project/*"
                )
                .hasRole("SERVICE_PROVIDER")

                .requestMatchers(
                        HttpMethod.POST,
                        "/api/project-phases/*/start"
                )
                .hasRole("SERVICE_PROVIDER")

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/project-phases/my"
                )
                .hasRole("SERVICE_PROVIDER")


                // ==================================================
                // PROJECT PHASES - ADMIN
                // ==================================================

                .requestMatchers(
                        HttpMethod.POST,
                        "/api/project-phases/*/approve",
                        "/api/project-phases/*/reject"
                )
                .hasRole("ADMINISTRATOR")

                .requestMatchers(
                        HttpMethod.POST,
                        "/api/project-phases/*/conclude"
                )
                .hasRole("ADMINISTRATOR")


                // ==================================================
                // PROJECT PHASES - ADMIN / OFFICER
                // ==================================================

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/project-phases",
                        "/api/project-phases/**"
                )
                .hasAnyRole(
                        "ADMINISTRATOR",
                        "VERIFICATION_OFFICER"
                )


                // ==================================================
                // EVIDENCE - SERVICE PROVIDER
                // ==================================================

                // Provider uploads evidence
                .requestMatchers(
                        HttpMethod.POST,
                        "/api/evidence/phase/*", "/api/evidence/phase/*/batch", "/api/evidence/*/withdraw"
                )
                .hasRole("SERVICE_PROVIDER")

                // Provider views own evidence history
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/evidence/my"
                )
                .hasRole("SERVICE_PROVIDER")


                // ==================================================
                // EVIDENCE - VERIFICATION OFFICER
                // ==================================================

                // Officer approves or rejects evidence
                .requestMatchers(
                        HttpMethod.POST,
                        "/api/evidence/*/approve",
                        "/api/evidence/*/reject"
                )
                .hasAnyRole("VERIFICATION_OFFICER", "ADMINISTRATOR")

                // Providers may open only their own files; ownership is enforced by the controller.
                .requestMatchers(HttpMethod.GET, "/api/evidence/*/file")
                .hasAnyRole("SERVICE_PROVIDER", "VERIFICATION_OFFICER", "ADMINISTRATOR")

                // Officer views all evidence and files
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/evidence",
                        "/api/evidence/**"
                )
                .hasAnyRole("VERIFICATION_OFFICER", "ADMINISTRATOR")


                // ==================================================
                // DISBURSEMENTS
                // ==================================================

                .requestMatchers(
                        HttpMethod.POST,
                        "/api/disbursements/**"
                )
                .hasRole("ADMINISTRATOR")

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/disbursements/my"
                )
                .hasRole("SERVICE_PROVIDER")

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/disbursements",
                        "/api/disbursements/**"
                )
                .hasRole("ADMINISTRATOR")


                // ==================================================
                // AUDIT LOGS
                // ==================================================

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/audit-logs",
                        "/api/audit-logs/**"
                )
                .hasRole("ADMINISTRATOR")


                // ==================================================
                // NOTIFICATIONS
                // ==================================================

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/notifications",
                        "/api/notifications/**"
                )
                .hasAnyRole(
                        "SERVICE_PROVIDER",
                        "VERIFICATION_OFFICER",
                        "ADMINISTRATOR"
                )

                .requestMatchers(
                        HttpMethod.POST,
                        "/api/notifications/**"
                )
                .hasAnyRole(
                        "SERVICE_PROVIDER",
                        "VERIFICATION_OFFICER",
                        "ADMINISTRATOR"
                )


                // ==================================================
                // EVERYTHING ELSE
                // ==================================================

                .anyRequest()
                .authenticated()
            )

            .addFilterBefore(new com.publicfund.backend.security.PublicAuthRateLimitFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(
                    jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
