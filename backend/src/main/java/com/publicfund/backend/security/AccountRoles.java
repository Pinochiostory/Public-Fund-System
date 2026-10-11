package com.publicfund.backend.security;

/** Keeps existing API/dashboard roles compatible with database-backed staff accounts. */
public final class AccountRoles {
    private AccountRoles() {}

    public static String loginRole(String storedRole) {
        if (storedRole == null) return null;
        return switch (storedRole) {
            case "ADMIN", "ADMINISTRATOR" -> "ADMINISTRATOR";
            case "GOVERNMENT_OFFICER", "VERIFICATION_OFFICER" -> "VERIFICATION_OFFICER";
            case "SERVICE_PROVIDER" -> "SERVICE_PROVIDER";
            default -> null;
        };
    }

    public static boolean isAdmin(String role) {
        return "ADMINISTRATOR".equals(loginRole(role));
    }
}
