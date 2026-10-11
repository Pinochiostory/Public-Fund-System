package com.publicfund.backend.repository;

import com.publicfund.backend.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByProjectIdOrApplicationIdOrderByCreatedAtAsc(Long projectId, Long applicationId);


    List<AuditLog> findByUserEmailOrderByCreatedAtDesc(
            String userEmail
    );

    List<AuditLog> findByActionOrderByCreatedAtDesc(
            String action
    );

    List<AuditLog> findAllByOrderByCreatedAtDesc();
}
