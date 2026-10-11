package com.publicfund.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "phase_evidence")
public class PhaseEvidence {
    @Column(length = 36)
    private String submissionId;
    public String getSubmissionId() { return submissionId; }
    public void setSubmissionId(String value) { submissionId = value; }

    @jakarta.persistence.Transient private String providerName;
    public String getProviderName() { return providerName; }
    public void setProviderName(String value) { providerName=value; }
    private Long fileSize;
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long value) { this.fileSize = value; }

    private String reviewedBy;
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String value) { this.reviewedBy = value; }

    @jakarta.persistence.Version
    @jakarta.persistence.Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;
    public long getVersion() { return version; }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long phaseId;

    @Column(nullable = false)
    private Long uploadedBy;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String fileType;

    @Column(nullable = false)
    private String filePath;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(columnDefinition = "TEXT")
    private String verificationComment;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @Column
    private LocalDateTime verifiedAt;

    public PhaseEvidence() {
    }

    public PhaseEvidence(
            Long phaseId,
            Long uploadedBy,
            String fileName,
            String fileType,
            String filePath) {

        this.phaseId = phaseId;
        this.uploadedBy = uploadedBy;
        this.fileName = fileName;
        this.fileType = fileType;
        this.filePath = filePath;
        this.status = "PENDING";
        this.uploadedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {

        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
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

    public Long getPhaseId() {
        return phaseId;
    }

    public void setPhaseId(Long phaseId) {
        this.phaseId = phaseId;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(Long uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVerificationComment() {
        return verificationComment;
    }

    public void setVerificationComment(String verificationComment) {
        this.verificationComment = verificationComment;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
}
