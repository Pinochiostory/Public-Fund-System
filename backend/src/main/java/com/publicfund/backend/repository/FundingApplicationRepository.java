package com.publicfund.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.publicfund.backend.entity.FundingApplication;

public interface FundingApplicationRepository
        extends JpaRepository<FundingApplication, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from FundingApplication r where r.id = :id")
    java.util.Optional<FundingApplication> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);


    List<FundingApplication> findByApplicantId(Long applicantId);

    List<FundingApplication> findByStatus(String status);
}
