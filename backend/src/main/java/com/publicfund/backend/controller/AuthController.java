package com.publicfund.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.publicfund.backend.dto.AuthResponse;
import com.publicfund.backend.security.AccountRoles;
import org.springframework.dao.DataIntegrityViolationException;
import com.publicfund.backend.entity.User;
import com.publicfund.backend.service.AdminAuthService;
import com.publicfund.backend.service.AuthService;
import com.publicfund.backend.service.PasswordResetService;
import com.publicfund.backend.service.RequiredPasswordChangeService;
import org.springframework.http.CacheControl;
import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    private final AuthService authService;
    private final com.publicfund.backend.service.EmailVerificationService emailVerification;
    private final AdminAuthService adminAuthService;
    private final PasswordResetService passwordResetService;
    private final RequiredPasswordChangeService requiredPasswordChangeService;

    public AuthController(com.publicfund.backend.service.EmailVerificationService emailVerification, 
            AuthService authService,
            AdminAuthService adminAuthService,
            PasswordResetService passwordResetService,
            RequiredPasswordChangeService requiredPasswordChangeService) {

        this.authService = authService;
        this.emailVerification = emailVerification;
        this.adminAuthService = adminAuthService;
        this.passwordResetService = passwordResetService;
        this.requiredPasswordChangeService = requiredPasswordChangeService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String role) {

        try {

            User user =
                    authService.registerUser(
                            fullName,
                            email,
                            password,
                            role
                    );

            AuthResponse response =
                    new AuthResponse(
                            user.getId(),
                            user.getFullName(),
                            user.getEmail(),
                            user.getRole(),
                            user.getAccountStatus()
                    );

            boolean codeSent = true;
            String message = "Account created. A verification code has been sent to your email.";
            try { emailVerification.sendCode(user.getEmail()); }
            catch (IllegalArgumentException delivery) {
                codeSent = false;
                message = "Account created, but it is not yet verified. " + delivery.getMessage();
            }
            return ResponseEntity.accepted().body(java.util.Map.of(
                    "email", user.getEmail(), "emailVerificationRequired", true,
                    "codeSent", codeSent, "message", message));

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }


    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String email, @RequestParam String code) {
        emailVerification.verify(email, code);
        return ResponseEntity.ok(java.util.Map.of("message", "Email verified. You can now sign in."));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestParam String email) {
        emailVerification.sendCode(email);
        return ResponseEntity.ok(java.util.Map.of("message", "A new verification code has been sent."));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestParam String email,
            @RequestParam String password) {

        try {

            AuthService.LoginResult result =
                    authService.loginUser(
                            email,
                            password
                    );

            User user =
                    result.getUser();

            AuthResponse response =
                    new AuthResponse(
                            user.getId(),
                            user.getFullName(),
                            user.getEmail(),
                            AccountRoles.loginRole(user.getRole()),
                            user.getAccountStatus(),
                            user.isMustChangePassword()
                    );

            return ResponseEntity.ok(
                    new LoginResponse(
                            response,
                            result.getToken()
                    )
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    // SecurityConfig restricts this endpoint to authenticated administrators.
    @PostMapping("/staff")
    public ResponseEntity<?> createStaff(
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String role) {
        try {
            User user = authService.createStaffUser(fullName, email, password, role);
            return ResponseEntity.status(201).body(new AuthResponse(user.getId(),
                    user.getFullName(), user.getEmail(), user.getRole(), user.getAccountStatus()));
        } catch (DataIntegrityViolationException e) {
            // Do not expose SQL or password hashes, including on concurrent duplicate requests.
            return ResponseEntity.status(409).body("An account already exists with these details.");
        } catch (IllegalArgumentException e) {
            return ApiErrors.response(e);
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body("Unable to create the account.");
        }
    }

    @PostMapping("/admin-login")
    public ResponseEntity<?> adminLogin(
            @RequestParam String email,
            @RequestParam String password) {

        try {

            AdminAuthService.AdminLoginResult result =
                    adminAuthService.loginAdmin(
                            email,
                            password
                    );

            return ResponseEntity.ok(
                    new LoginResponse(
                            result.getUser(),
                            result.getToken()
                    )
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changeRequiredPassword(Principal principal,
            @RequestParam String temporaryPassword, @RequestParam String newPassword) {
        try {
            AuthService.LoginResult result = requiredPasswordChangeService.change(
                    principal.getName(), temporaryPassword, newPassword);
            User user = result.getUser();
            AuthResponse response = new AuthResponse(user.getId(), user.getFullName(), user.getEmail(),
                    AccountRoles.loginRole(user.getRole()), user.getAccountStatus(), user.isMustChangePassword());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(new LoginResponse(response, result.getToken()));
        } catch (IllegalArgumentException e) {
            return ApiErrors.response(e);
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @RequestParam String email) {

        try {

            passwordResetService.requestPasswordReset(email);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                    "If an eligible account exists for this email, a password reset link has been sent. Check your inbox and spam folder.");
        } catch (RuntimeException e) {
            return ApiErrors.response(e);
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @RequestParam String token,
            @RequestParam String newPassword) {

        try {

            passwordResetService
                    .resetPassword(
                            token,
                            newPassword
                    );

            return ResponseEntity.ok(
                    "Password reset successfully"
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    public static class LoginResponse {

        private final AuthResponse user;
        private final String token;

        public LoginResponse(
                AuthResponse user,
                String token) {

            this.user = user;
            this.token = token;
        }

        public AuthResponse getUser() {
            return user;
        }

        public String getToken() {
            return token;
        }
    }

}
