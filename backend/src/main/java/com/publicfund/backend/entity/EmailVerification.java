package com.publicfund.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "email_verifications")
public class EmailVerification {
    @Id public Long userId;
    @Column(length = 100) public String codeHash;
    public LocalDateTime expiresAt;
    public LocalDateTime lastSentAt;
    public LocalDateTime windowStartedAt;
    public int sendCount;
    public int attempts;
}
