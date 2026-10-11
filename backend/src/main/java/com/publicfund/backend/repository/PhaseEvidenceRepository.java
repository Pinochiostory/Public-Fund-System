package com.publicfund.backend.repository;

import com.publicfund.backend.entity.PhaseEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhaseEvidenceRepository
        extends JpaRepository<PhaseEvidence, Long> {
    @org.springframework.data.jpa.repository.Query("select r.phaseId from PhaseEvidence r where r.id = :id")
    java.util.Optional<Long> findParentId(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from PhaseEvidence r where r.id = :id")
    java.util.Optional<PhaseEvidence> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);


    List<PhaseEvidence> findByPhaseId(Long phaseId);

    List<PhaseEvidence> findByStatus(String status);

    List<PhaseEvidence> findByPhaseIdAndStatus(
            Long phaseId,
            String status
    );
}
