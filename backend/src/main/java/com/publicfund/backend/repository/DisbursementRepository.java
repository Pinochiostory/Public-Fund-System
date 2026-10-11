package com.publicfund.backend.repository;

import com.publicfund.backend.entity.Disbursement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DisbursementRepository
        extends JpaRepository<Disbursement, Long> {
    @org.springframework.data.jpa.repository.Query("select r.phaseId from Disbursement r where r.id = :id")
    java.util.Optional<Long> findParentId(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Disbursement r where r.id = :id")
    java.util.Optional<Disbursement> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);


    List<Disbursement> findByProjectId(Long projectId);

    List<Disbursement> findByPhaseId(Long phaseId);

    List<Disbursement> findByStatus(String status);

    Optional<Disbursement> findByPhaseIdAndStatus(
            Long phaseId,
            String status
    );
}
