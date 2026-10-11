package com.publicfund.backend.repository;

import com.publicfund.backend.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository
        extends JpaRepository<Project, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Project r where r.id = :id")
    java.util.Optional<Project> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);


    Optional<Project> findByApplicationId(Long applicationId);

    List<Project> findByApplicantId(Long applicantId);

    List<Project> findByStatus(String status);
}
