package com.publicfund.backend.service;

import com.publicfund.backend.entity.AuditLog;
import com.publicfund.backend.entity.User;
import com.publicfund.backend.repository.AuditLogRepository;
import com.publicfund.backend.repository.UserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository) {

        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    public AuditLog logAction(
            Long userId,
            String userEmail,
            String userRole,
            String action,
            String description) {

        AuditLog auditLog =
                new AuditLog(
                        userId,
                        userEmail,
                        userRole,
                        action,
                        description
                );

        return auditLogRepository.save(auditLog);
    }

    public AuditLog logCurrentUser(
            String action,
            String description) {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            return logAction(
                    null,
                    "SYSTEM",
                    "SYSTEM",
                    action,
                    description
            );
        }

        String email =
                authentication.getName();

        String role =
                authentication.getAuthorities()
                        .stream()
                        .findFirst()
                        .map(authority ->
                                authority.getAuthority()
                                        .replace("ROLE_", "")
                        )
                        .orElse("UNKNOWN");

        Long userId = userRepository.findByEmail(email)
                .map(User::getId).orElse(null);

        return logAction(
                userId,
                email,
                role,
                action,
                description
        );
    }


    public AuditLog logProjectAction(Long projectId, String action, String description) {
        AuditLog log = logCurrentUser(action, description);
        log.setProjectId(projectId);
        return auditLogRepository.save(log);
    }

    public AuditLog logApplicationAction(Long applicationId, String action, String description) {
        AuditLog log = logCurrentUser(action, description);
        log.setApplicationId(applicationId);
        return auditLogRepository.save(log);
    }

    public List<AuditLog> projectActivity(Long projectId, Long applicationId) {
        return auditLogRepository.findByProjectIdOrApplicationIdOrderByCreatedAtAsc(projectId, applicationId);
    }

    public List<AuditLog> getAllAuditLogs() {

        return auditLogRepository
                .findAllByOrderByCreatedAtDesc();
    }

    public List<AuditLog> getLogsByUserEmail(
            String email) {

        return auditLogRepository
                .findByUserEmailOrderByCreatedAtDesc(
                        email
                );
    }

    public List<AuditLog> getLogsByAction(
            String action) {

        return auditLogRepository
                .findByActionOrderByCreatedAtDesc(
                        action
                );
    }
}
