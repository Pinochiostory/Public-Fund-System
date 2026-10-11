package com.publicfund.backend.service;

import com.publicfund.backend.entity.User;
import com.publicfund.backend.repository.UserRepository;
import com.publicfund.backend.repository.PasswordResetTokenRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequiredPasswordChangeService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final JwtService jwt;
    private final AuditLogService audit;
    private final PasswordResetTokenRepository resetTokens;

    public RequiredPasswordChangeService(UserRepository users, PasswordEncoder encoder, AuthService auth,
            JwtService jwt, AuditLogService audit, PasswordResetTokenRepository resetTokens) {
        this.users = users;
        this.encoder = encoder;
        this.auth = auth;
        this.jwt = jwt;
        this.audit = audit;
        this.resetTokens = resetTokens;
    }

    @Transactional
    public AuthService.LoginResult change(String email, String temporaryPassword, String newPassword) {
        User user = users.findByEmailForUpdate(email)
                .orElseThrow(() -> new AccessDeniedException("Account unavailable"));
        if (!"ACTIVE".equals(user.getAccountStatus()) || !user.isMustChangePassword()) {
            throw new AccessDeniedException("Password change is not available for this session");
        }
        if (temporaryPassword == null || !encoder.matches(temporaryPassword, user.getPassword())) {
            throw new IllegalArgumentException("The temporary password is incorrect. Please sign in again if it was reset.");
        }
        auth.validatePassword(newPassword);
        if (encoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("Choose a new password different from the temporary password.");
        }
        user.setPassword(encoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setFailedLoginAttempts(0);
        users.save(user);
        resetTokens.deleteByUserId(user.getId());
        audit.logAction(user.getId(), user.getEmail(), user.getRole(), "REQUIRED_PASSWORD_CHANGED",
                "User completed the required password change");
        return new AuthService.LoginResult(user, jwt.generateAccountToken(user));
    }
}
