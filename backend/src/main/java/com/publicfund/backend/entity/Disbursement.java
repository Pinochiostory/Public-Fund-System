package com.publicfund.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "disbursements", uniqueConstraints = @UniqueConstraint(name = "uk_disbursement_phase", columnNames = "phaseId"))
public class Disbursement {
    private String paymentReference;
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String value) { this.paymentReference = value; }

    private String recordedBy;
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String value) { this.recordedBy = value; }

    private String proofName;
    public String getProofName() { return proofName; }
    public void setProofName(String value) { this.proofName = value; }

    private String proofType;
    public String getProofType() { return proofType; }
    public void setProofType(String value) { this.proofType = value; }

    private Long proofSize;
    public Long getProofSize() { return proofSize; }
    public void setProofSize(Long value) { this.proofSize = value; }

    private String proofPath;
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getProofPath() { return proofPath; }
    public void setProofPath(String value) { this.proofPath = value; }

    private java.time.LocalDateTime proofUploadedAt;
    public java.time.LocalDateTime getProofUploadedAt() { return proofUploadedAt; }
    public void setProofUploadedAt(java.time.LocalDateTime value) { this.proofUploadedAt = value; }

    private String proofUploadedBy;
    public String getProofUploadedBy() { return proofUploadedBy; }
    public void setProofUploadedBy(String value) { this.proofUploadedBy = value; }

    @jakarta.persistence.Version
    @jakarta.persistence.Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;
    public long getVersion() { return version; }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long phaseId;

    @Column(
        nullable = false,
        precision = 15,
        scale = 2
    )
    private BigDecimal amount;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime releasedAt;

    public Disbursement() {
    }

    public Disbursement(
            Long projectId,
            Long phaseId,
            BigDecimal amount) {

        this.projectId = projectId;
        this.phaseId = phaseId;
        this.amount = amount;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (status == null) {
            status = "PENDING";
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(
            Long projectId) {

        this.projectId = projectId;
    }

    public Long getPhaseId() {
        return phaseId;
    }

    public void setPhaseId(
            Long phaseId) {

        this.phaseId = phaseId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(
            BigDecimal amount) {

        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(
            String status) {

        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(
            LocalDateTime createdAt) {

        this.createdAt = createdAt;
    }

    public LocalDateTime getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(
            LocalDateTime releasedAt) {

        this.releasedAt = releasedAt;
    }
}
