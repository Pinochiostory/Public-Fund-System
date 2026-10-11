package com.publicfund.backend.repository;

import com.publicfund.backend.entity.ProjectReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectReconciliationRepository extends JpaRepository<ProjectReconciliation, Long> {
    List<ProjectReconciliation> findByProjectIdOrderByIdAsc(Long projectId);
}
