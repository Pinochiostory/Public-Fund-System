package com.publicfund.backend.service;

import com.publicfund.backend.dto.AuthResponse;
import com.publicfund.backend.entity.User;
import com.publicfund.backend.security.AccountRoles;
import org.springframework.stereotype.Service;

/** Compatibility adapter for the existing /auth/admin-login endpoint. */
@Service
public class AdminAuthService {
    private final AuthService authService;

    public AdminAuthService(AuthService authService) {
        this.authService = authService;
    }

    public AdminLoginResult loginAdmin(String email, String password) {
        AuthService.LoginResult result = authService.loginAdmin(email, password);
        User user = result.getUser();
        return new AdminLoginResult(new AuthResponse(user.getId(), user.getFullName(),
                user.getEmail(), AccountRoles.loginRole(user.getRole()), user.getAccountStatus(), user.isMustChangePassword()),
                result.getToken());
    }

    public static class AdminLoginResult {
        private final AuthResponse user;
        private final String token;

        public AdminLoginResult(AuthResponse user, String token) {
            this.user = user;
            this.token = token;
        }

        public AuthResponse getUser() { return user; }
        public String getToken() { return token; }
    }
}
