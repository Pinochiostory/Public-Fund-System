package com.publicfund.backend.repository;
import com.publicfund.backend.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {}
