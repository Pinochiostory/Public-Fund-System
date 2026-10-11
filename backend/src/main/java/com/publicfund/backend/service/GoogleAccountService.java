package com.publicfund.backend.service;

import com.publicfund.backend.entity.User;
import com.publicfund.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleAccountService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AuthService auth;
    private final JwtService jwt;
    private final AuditLogService audit;
    public GoogleAccountService(UserRepository users, PasswordEncoder passwords, AuthService auth, JwtService jwt, AuditLogService audit) {
        this.users=users; this.passwords=passwords; this.auth=auth; this.jwt=jwt; this.audit=audit;
    }
    public static class LinkRequired extends IllegalArgumentException {
        public LinkRequired() { super("An account already uses this email. Confirm its current password to connect Google."); }
    }
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public AuthService.LoginResult login(GoogleIdentityVerifier.Identity identity, String existingPassword) {
        User linked=users.findByGoogleSubject(identity.subject()).orElse(null);
        User user=linked == null ? users.findByEmailForUpdate(identity.email()).orElse(null)
                : users.findByIdForUpdate(linked.getId()).orElseThrow();
        if (user == null) {
            if (!identity.authoritativeEmail())
                throw new IllegalArgumentException("Register and verify this email first, then connect Google using your password.");
            user=new User(identity.name(), identity.email(), passwords.encode(java.util.UUID.randomUUID()+"Aa1!"), "SERVICE_PROVIDER");
            user.setGoogleSubject(identity.subject());
            user.setEmailVerified(true);
            user=users.saveAndFlush(user);
            audit.logAction(user.getId(), user.getEmail(), user.getRole(), "GOOGLE_ACCOUNT_CREATED", "Service Provider registered using verified Google identity");
        } else {
            if (!"SERVICE_PROVIDER".equals(user.getRole()))
                throw new IllegalArgumentException("Staff accounts must sign in with their existing email and password.");
            if (!"ACTIVE".equals(user.getAccountStatus()))
                throw new IllegalArgumentException("Account is not active. Contact an administrator.");
            if (user.isMustChangePassword())
                throw new IllegalArgumentException("Sign in with your temporary password and create a new password before using Google.");
            if (!user.isEmailVerified())
                throw new IllegalArgumentException("Verify your email before connecting Google.");
            if (user.getGoogleSubject() == null) {
                if (existingPassword == null || existingPassword.isBlank()) throw new LinkRequired();
                // Reuse password verification and the existing failed-attempt/lockout rules.
                auth.loginUser(user.getEmail(), existingPassword);
                user.setGoogleSubject(identity.subject());
                users.saveAndFlush(user);
                audit.logAction(user.getId(), user.getEmail(), user.getRole(), "GOOGLE_ACCOUNT_LINKED", "Google connected after confirming existing account password");
            } else if (!identity.subject().equals(user.getGoogleSubject())) {
                throw new IllegalArgumentException("This account is connected to a different Google identity.");
            }
        }
        return new AuthService.LoginResult(user, jwt.generateAccountToken(user));
    }
}
