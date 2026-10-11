package com.publicfund.backend.dto;

import com.publicfund.backend.entity.User;
import com.publicfund.backend.security.AccountRoles;

/** Deliberately excludes passwords, password hashes and reset tokens. */
public record ManagedUserResponse(Long id, String fullName, String email, String role,
        String accountStatus, boolean mustChangePassword) {
    public static ManagedUserResponse from(User user) {
        String compatible = AccountRoles.loginRole(user.getRole());
        String role = "ADMINISTRATOR".equals(compatible) ? "ADMIN"
                : "VERIFICATION_OFFICER".equals(compatible) ? "GOVERNMENT_OFFICER" : user.getRole();
        return new ManagedUserResponse(user.getId(), user.getFullName(), user.getEmail(),
                role, user.getAccountStatus(), user.isMustChangePassword());
    }
}
