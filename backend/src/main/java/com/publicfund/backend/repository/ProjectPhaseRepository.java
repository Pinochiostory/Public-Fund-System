package com.publicfund.backend.repository;

import com.publicfund.backend.entity.ProjectPhase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectPhaseRepository
        extends JpaRepository<ProjectPhase, Long> {
    @org.springframework.data.jpa.repository.Query("select r.projectId from ProjectPhase r where r.id = :id")
    java.util.Optional<Long> findParentId(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from ProjectPhase r where r.id = :id")
    java.util.Optional<ProjectPhase> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);


    List<ProjectPhase> findByProjectId(Long projectId);

    List<ProjectPhase> findByProjectIdOrderByPhaseOrderAsc(
            Long projectId
    );

    List<ProjectPhase> findByStatus(String status);
}
