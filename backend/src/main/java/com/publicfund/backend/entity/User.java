package com.publicfund.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(unique = true, length = 255)
    private String googleSubject;
    public String getGoogleSubject() { return googleSubject; }
    public void setGoogleSubject(String value) { googleSubject = value; }

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean emailVerified = true;
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean value) { emailVerified = value; }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String accountStatus = "ACTIVE";

    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean mustChangePassword = false;

    public boolean isMustChangePassword() { return mustChangePassword; }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public User() {
    }

    public int getFailedLoginAttempts() {
    return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public User(String fullName, String email, String password, String role) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.role = role;
        this.accountStatus = "ACTIVE";
        this.failedLoginAttempts = 0;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }
}
