package com.publicfund.backend.service;

import com.publicfund.backend.entity.PasswordResetToken;
import com.publicfund.backend.entity.User;
import com.publicfund.backend.repository.PasswordResetTokenRepository;
import com.publicfund.backend.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final AuditLogService auditLogService;
    private final EmailDeliveryService mail;
    private final String frontendUrl;
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    public static String tokenHash(String token) {
        try { return "h:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public PasswordResetService(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthService authService,
            AuditLogService auditLogService, EmailDeliveryService mail,
            @org.springframework.beans.factory.annotation.Value("${PUBLIC_FUND_FRONTEND_URL:http://localhost:5173}") String frontendUrl) {

        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.auditLogService = auditLogService;
        this.mail = mail;
        java.net.URI url=java.net.URI.create(frontendUrl);
        if (url.getHost()==null || url.getUserInfo()!=null || url.getQuery()!=null || url.getFragment()!=null
                || (!"https".equals(url.getScheme()) && !("http".equals(url.getScheme())
                && java.util.Set.of("localhost","127.0.0.1").contains(url.getHost()))))
            throw new IllegalArgumentException("Frontend URL must use HTTPS (HTTP is allowed only for localhost).");
        this.frontendUrl=frontendUrl.replaceAll("/+$","");
    }

    @Transactional
    public String requestPasswordReset(
            String email) {

        if (email == null ||
                email.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Email is required"
            );
        }

        if (!mail.isConfigured()) throw new IllegalArgumentException("Email delivery is not configured. Contact the system owner.");
        String normalizedEmail =
                email.trim().toLowerCase(java.util.Locale.ROOT);

        User user =
                userRepository
                        .findByEmailForUpdate(normalizedEmail)
                        .orElse(null);

        /*
         * Do not reveal whether an email exists.
         */
        // Public recovery remains Service Provider-only; staff use existing Admin-assisted recovery.
        if (user == null || !"SERVICE_PROVIDER".equals(user.getRole())
                || "DISABLED".equals(user.getAccountStatus()) || user.isMustChangePassword()) {
            return null;
        }

        /*
         * Remove previous reset tokens
         * for this user.
         */
        tokenRepository.deleteByUserId(
                user.getId()
        );

        byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes);
        String token=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PasswordResetToken resetToken =
                new PasswordResetToken(
                        user.getId(),
                        tokenHash(token),
                        LocalDateTime.now()
                                .plusMinutes(30)
                );

        tokenRepository.save(
                resetToken
        );

        auditLogService.logAction(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                "PASSWORD_RESET_REQUESTED",
                "Password reset requested"
        );

        mail.send(user.getEmail(), "Reset your Public Fund password",
                "You requested a password reset. Open this link within 30 minutes:\n\n"
                + frontendUrl + "/reset-password#token=" + token
                + "\n\nIf you did not request this, ignore this email.");
        return null; // Never return a reset credential to the HTTP caller.
    }

    @Transactional
    public void resetPassword(
            String token,
            String newPassword) {

        if (token == null ||
                token.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Reset token is required"
            );
        }

        authService.validatePassword(
                newPassword
        );

        String hash=tokenHash(token.trim());
        Long userId=tokenRepository.findUserIdByToken(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired password reset link."));
        User user=userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("User account not found"));
        PasswordResetToken resetToken =
                tokenRepository
                        .findByToken(
                                hash
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid password reset token"
                                ));

        if (resetToken.isUsed()) {

            throw new IllegalArgumentException(
                    "Password reset token has already been used"
            );
        }

        if (resetToken.getExpiresAt()
                .isBefore(LocalDateTime.now())) {

            throw new IllegalArgumentException(
                    "Password reset token has expired"
            );
        }



        if (!"SERVICE_PROVIDER".equals(user.getRole())) {
            throw new IllegalArgumentException("Staff password recovery requires a secure administrator-assisted process.");
        }

        if ("DISABLED".equals(user.getAccountStatus()) || user.isMustChangePassword()) {
            throw new IllegalArgumentException("This account requires administrator assistance or the temporary-password login flow.");
        }

        user.setPassword(
                passwordEncoder.encode(
                        newPassword
                )
        );

        /*
         * Reset lockout state as well.
         */
        user.setFailedLoginAttempts(0);
        user.setAccountStatus("ACTIVE");

        userRepository.save(user);

        resetToken.setUsed(true);

        tokenRepository.save(
                resetToken
        );

        auditLogService.logAction(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                "PASSWORD_RESET_COMPLETED",
                "Password reset completed successfully"
        );
    }
}
