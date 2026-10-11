package com.publicfund.backend.service;

import com.publicfund.backend.dto.ManagedUserResponse;
import com.publicfund.backend.entity.User;
import com.publicfund.backend.repository.PasswordResetTokenRepository;
import com.publicfund.backend.repository.UserRepository;
import com.publicfund.backend.security.AccountRoles;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserManagementService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordResetTokenRepository resetTokens;
    private final AuditLogService audit;
    private final SecureRandom random = new SecureRandom();

    public UserManagementService(UserRepository users, PasswordEncoder encoder,
            PasswordResetTokenRepository resetTokens, AuditLogService audit) {
        this.users = users;
        this.encoder = encoder;
        this.resetTokens = resetTokens;
        this.audit = audit;
    }

    private User requireAdmin(String email) {
        User admin = users.findByEmail(email).orElseThrow(() -> new AccessDeniedException("Administrator access required"));
        if (!AccountRoles.isAdmin(admin.getRole()) || !"ACTIVE".equals(admin.getAccountStatus()) || admin.isMustChangePassword()) {
            throw new AccessDeniedException("An active administrator account is required");
        }
        return admin;
    }

    private User target(Long id) {
        return users.findByIdForUpdate(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional(readOnly = true)
    public List<ManagedUserResponse> listUsers(String actorEmail) {
        requireAdmin(actorEmail);
        return users.findAll().stream().sorted(Comparator.comparing(User::getId))
                .map(ManagedUserResponse::from).toList();
    }

    @Transactional
    public ManagedUserResponse setStatus(String actorEmail, Long id, String status) {
        User admin = requireAdmin(actorEmail);
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new IllegalArgumentException("Status must be ACTIVE or DISABLED.");
        }
        User user = target(id);
        String role = AccountRoles.loginRole(user.getRole());
        if (Objects.equals(admin.getId(), user.getId()) ||
                !("SERVICE_PROVIDER".equals(role) || "VERIFICATION_OFFICER".equals(role))) {
            throw new AccessDeniedException("Only Service Provider and Government Officer status may be changed.");
        }
        if (!status.equals(user.getAccountStatus())) {
            user.setAccountStatus(status);
            if ("ACTIVE".equals(status)) user.setFailedLoginAttempts(0);
            users.save(user);
            record(admin, user, "ACTIVE".equals(status) ? "ADMIN_ENABLED_USER" : "ADMIN_DISABLED_USER");
        }
        return ManagedUserResponse.from(user);
    }

    @Transactional
    public TemporaryPasswordResponse resetPassword(String actorEmail, Long id) {
        User admin = requireAdmin(actorEmail);
        User user = target(id);
        if (Objects.equals(admin.getId(), user.getId())) {
            throw new AccessDeniedException("You cannot reset your own password through User Management.");
        }
        if (AccountRoles.loginRole(user.getRole()) == null) {
            throw new IllegalArgumentException("Unsupported account role.");
        }
        byte[] entropy = new byte[24];
        random.nextBytes(entropy);
        String temporary = "Aa1!" + Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        user.setPassword(encoder.encode(temporary));
        user.setMustChangePassword(true);
        user.setFailedLoginAttempts(0);
        // A reset can recover a password lockout, but never overrides an administrative disable.
        if ("LOCKED".equals(user.getAccountStatus())) user.setAccountStatus("ACTIVE");
        resetTokens.deleteByUserId(user.getId());
        users.save(user);
        record(admin, user, "ADMIN_INITIATED_PASSWORD_RESET");
        return new TemporaryPasswordResponse(ManagedUserResponse.from(user), temporary);
    }

    private void record(User admin, User user, String action) {
        audit.logAction(admin.getId(), admin.getEmail(), admin.getRole(), action,
                "Affected user ID " + user.getId() + " (" + user.getEmail() + ")");
    }

    public record TemporaryPasswordResponse(ManagedUserResponse user, String temporaryPassword) {
        @Override public String toString() { return "TemporaryPasswordResponse[REDACTED]"; }
    }
}
