package com.publicfund.backend.dto;

public class AuthResponse {

    private Long id;
    private String fullName;
    private String email;
    private String role;
    private String accountStatus;
    private boolean mustChangePassword;

    public AuthResponse() {
    }

    public AuthResponse(Long id, String fullName, String email,
                        String role, String accountStatus) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.accountStatus = accountStatus;
    }

    public Long getId() {
        return id;
    }

    public AuthResponse(Long id, String fullName, String email, String role,
                        String accountStatus, boolean mustChangePassword) {
        this(id, fullName, email, role, accountStatus);
        this.mustChangePassword = mustChangePassword;
    }

    public boolean isMustChangePassword() { return mustChangePassword; }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public String getAccountStatus() {
        return accountStatus;
    }
}
