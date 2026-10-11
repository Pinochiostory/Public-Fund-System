package com.publicfund.backend.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.publicfund.backend.service.JwtService;
import com.publicfund.backend.entity.User;
import com.publicfund.backend.repository.UserRepository;
import io.jsonwebtoken.Claims;
import java.util.Objects;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

@Component
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository users;

    public JwtAuthenticationFilter(
            JwtService jwtService, UserRepository users) {

        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authorizationHeader =
                request.getHeader("Authorization");

        /*
         * No JWT token supplied.
         * Continue normally.
         *
         * Spring Security will decide whether
         * the endpoint is public or protected.
         */
        if (authorizationHeader == null ||
                !authorizationHeader.startsWith("Bearer ")) {

            filterChain.doFilter(
                    request,
                    response
            );

            return;
        }

        String token =
                authorizationHeader.substring(7);

        try {

            Claims claims = jwtService.extractClaims(token);
            String email = claims.getSubject();
            String role = AccountRoles.loginRole(claims.get("role", String.class));
            Long userId = claims.get("userId", Long.class);
            User user = email == null ? null : users.findByEmail(email).orElse(null);

            // All roles must resolve to an active database account. Old synthetic staff
            // tokens (IDs 0/-1) no longer grant access, even if their signatures are valid.
            if (user != null && userId != null && userId > 0 &&
                    Objects.equals(userId, user.getId()) && role != null &&
                    role.equals(AccountRoles.loginRole(user.getRole())) &&
                    "ACTIVE".equals(user.getAccountStatus()) &&
                    user.isEmailVerified() &&
                    jwtService.isCurrentAccountToken(claims, user) &&
                    SecurityContextHolder
                            .getContext()
                            .getAuthentication() == null) {

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                email,
                                null,
                                List.of(
                                        new SimpleGrantedAuthority(
                                                "ROLE_" + (user.isMustChangePassword() ? "PASSWORD_CHANGE_REQUIRED" : role)
                                        )
                                )
                        );

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(
                                authentication
                        );
            }

        } catch (Exception e) {

            /*
             * Invalid JWT.
             * Do not authenticate the request.
             *
             * Spring Security will reject it later
             * if the endpoint requires authentication.
             */
        }

        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        boolean publicAuth = java.util.Set.of("/api/auth/login", "/api/auth/admin-login",
                "/api/auth/register", "/api/auth/verify-email", "/api/auth/resend-verification", "/api/auth/forgot-password", "/api/auth/reset-password")
                .contains(requestPath);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!publicAuth && authentication == null) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Session expired or account unavailable. Please sign in again.\"}");
            return;
        }
        if (!publicAuth && authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PASSWORD_CHANGE_REQUIRED"))
                && !"/api/auth/change-password".equals(requestPath)) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"PASSWORD_CHANGE_REQUIRED\",\"message\":\"Change your temporary password before continuing.\"}");
            return;
        }

        filterChain.doFilter(
                request,
                response
        );
    }
}
