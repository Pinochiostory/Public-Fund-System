package com.publicfund.backend.security;

import com.publicfund.backend.entity.Project;
import com.publicfund.backend.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ProjectAccess {
    private final UserRepository users;
    public ProjectAccess(UserRepository users) { this.users = users; }
    public void requireOwner(Project project) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var user = authentication == null ? null : users.findByEmail(authentication.getName()).orElse(null);
        if (user == null || !"ACTIVE".equals(user.getAccountStatus()) || !user.isEmailVerified()
                || !java.util.Objects.equals(user.getId(), project.getApplicantId()))
            throw new AccessDeniedException("You do not have permission to perform this action.");
    }
    public void requireReader(Project project) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRATOR"))) return;
        requireOwner(project);
    }
    public Long requireAdministratorId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        var user = auth == null ? null : users.findByEmail(auth.getName()).orElse(null);
        if (user == null || !"ACTIVE".equals(user.getAccountStatus())
                || !AccountRoles.isAdmin(user.getRole())
                || auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMINISTRATOR".equals(a.getAuthority())))
            throw new AccessDeniedException("You do not have permission to perform this action.");
        return user.getId();
    }

    public static String actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "SYSTEM" : auth.getName();
    }
}
