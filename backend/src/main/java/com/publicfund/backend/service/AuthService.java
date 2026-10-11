package com.publicfund.backend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.publicfund.backend.entity.User;
import com.publicfund.backend.security.AccountRoles;
import com.publicfund.backend.repository.UserRepository;
import java.util.Locale;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public User registerUser(
            String fullName,
            String email,
            String password,
            String role) {

        if (!"SERVICE_PROVIDER".equals(role)) {
            throw new IllegalArgumentException("Only service providers can register publicly.");
        }
        return createUser(fullName, email, password, "SERVICE_PROVIDER");
    }

    // Invoked only by the admin-protected controller or the startup bootstrap.
    public User createStaffUser(String fullName, String email, String password, String role) {
        if (!"ADMIN".equals(role) && !"GOVERNMENT_OFFICER".equals(role)) {
            throw new IllegalArgumentException("Staff role must be ADMIN or GOVERNMENT_OFFICER.");
        }
        return createUser(fullName, email, password, role);
    }

    private User createUser(String fullName, String email, String password, String role) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Full name is required"
            );
        }

        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Email is required"
            );
        }

        if (fullName.trim().length() > 255 || email.trim().length() > 255) {
            throw new IllegalArgumentException("Name and email must not exceed 255 characters.");
        }

        if (!email.trim().matches(
                "^[A-Za-z0-9+_.-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")) {

            throw new IllegalArgumentException(
                    "Invalid email address"
            );
        }

        validatePassword(password);

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(normalizedEmail)) {

            throw new IllegalArgumentException(
                    "Email already registered"
            );
        }

        User user = new User();

        user.setFullName(fullName.trim());
        user.setEmail(normalizedEmail);

        user.setPassword(
                passwordEncoder.encode(password)
        );

        user.setRole(role);
        user.setEmailVerified(!"SERVICE_PROVIDER".equals(role));
        user.setAccountStatus("ACTIVE");
        user.setFailedLoginAttempts(0);

        return userRepository.save(user);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public LoginResult loginUser(
            String email,
            String password) {

        return loginUser(email, password, false);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public LoginResult loginAdmin(String email, String password) {
        return loginUser(email, password, true);
    }

    private LoginResult loginUser(String email, String password, boolean adminOnly) {

        if (email == null || email.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Invalid email or password"
            );
        }

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        User user =
                userRepository
                        .findByEmailForUpdate(normalizedEmail)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid email or password"
                                ));

        String loginRole = AccountRoles.loginRole(user.getRole());
        if (loginRole == null || (adminOnly && !AccountRoles.isAdmin(user.getRole()))) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (user.getAccountStatus()
                .equals("LOCKED")) {

            throw new IllegalArgumentException(
                    "SERVICE_PROVIDER".equals(user.getRole())
                            ? "Account is locked. Please reset your password or contact an administrator"
                            : "Account is locked. Contact the system owner for staff account recovery."
            );
        }

        if (!user.getAccountStatus()
                .equals("ACTIVE")) {

            throw new IllegalArgumentException(
                    "Account is not active"
            );
        }

        if (password == null || !passwordEncoder.matches(
                password,
                user.getPassword())) {

            int failedAttempts =
                    user.getFailedLoginAttempts() + 1;

            user.setFailedLoginAttempts(
                    failedAttempts
            );

            if (failedAttempts >= 3) {

                user.setAccountStatus("LOCKED");

                userRepository.save(user);

                throw new IllegalArgumentException(
                        "Account locked after 3 failed login attempts"
                );
            }

            userRepository.save(user);

            int attemptsRemaining =
                    3 - failedAttempts;

            throw new IllegalArgumentException(
                    "Invalid email or password. "
                            + attemptsRemaining
                            + " login attempt(s) remaining"
            );
        }

        if (user.getFailedLoginAttempts() > 0) {

            user.setFailedLoginAttempts(0);

            userRepository.save(user);
        }

        if ("SERVICE_PROVIDER".equals(user.getRole()) && !user.isEmailVerified()) throw new IllegalArgumentException("Verify your email before signing in. Use Verify email to enter or resend your code.");

        String token =
                jwtService.generateAccountToken(user);

        return new LoginResult(
                user,
                token
        );
    }

    public void validatePassword(
            String password) {

        if (password == null) {

            throw new IllegalArgumentException(
                    "Password is required"
            );
        }

        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes.");

        if (password.length() < 8) {

            throw new IllegalArgumentException(
                    "Password must be at least 8 characters long"
            );
        }

        if (!password.matches(".*[A-Z].*")) {

            throw new IllegalArgumentException(
                    "Password must contain at least one uppercase letter"
            );
        }

        if (!password.matches(".*[a-z].*")) {

            throw new IllegalArgumentException(
                    "Password must contain at least one lowercase letter"
            );
        }

        if (!password.matches(".*[0-9].*")) {

            throw new IllegalArgumentException(
                    "Password must contain at least one number"
            );
        }

        if (!password.matches(
                ".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {

            throw new IllegalArgumentException(
                    "Password must contain at least one special character"
            );
        }
    }

    public static class LoginResult {

        private final User user;
        private final String token;

        public LoginResult(
                User user,
                String token) {

            this.user = user;
            this.token = token;
        }

        public User getUser() {
            return user;
        }

        public String getToken() {
            return token;
        }
    }
}
