package com.publicfund.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Append-only submissions: declarations and return claims. Rejections are retained. */
@Entity
@Table(name = "project_reconciliations", indexes = @Index(name = "ix_reconciliation_project", columnList = "projectId"))
public class ProjectReconciliation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version @Column(nullable = false)
    private long version;
    @Column(nullable = false)
    private Long projectId;
    @Column(nullable = false, length = 20)
    private String kind;
    
    private Long declarationId;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal releasedAmount;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false)
    private Long submittedBy;
    @Column(nullable = false)
    private LocalDateTime submittedAt;
    @Column(nullable = false, length = 2000)
    private String note;
    @com.fasterxml.jackson.annotation.JsonIgnore @Column(nullable = false, length = 1000)
    private String filePath;
    @Column(nullable = false, length = 200)
    private String fileName;
    @Column(nullable = false, length = 100)
    private String fileType;
    @Column(nullable = false)
    private Long fileSize;
    @Column(length = 1500)
    private String returnDestination;
    @Column(length = 100)
    private String transferReference;
    
    private LocalDate transferredOn;
    
    private Long reviewedBy;
    
    private LocalDateTime reviewedAt;
    @Column(length = 2000)
    private String reviewNote;
    @Column(length = 100)
    private String receiptReference;
    @com.fasterxml.jackson.annotation.JsonIgnore @Column(length = 100, unique = true)
    private String receiptKey;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId = value; }
    public String getKind() { return kind; }
    public void setKind(String value) { kind = value; }
    public Long getDeclarationId() { return declarationId; }
    public void setDeclarationId(Long value) { declarationId = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public BigDecimal getReleasedAmount() { return releasedAmount; }
    public void setReleasedAmount(BigDecimal value) { releasedAmount = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Long getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(Long value) { submittedBy = value; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime value) { submittedAt = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String value) { filePath = value; }
    public String getFileName() { return fileName; }
    public void setFileName(String value) { fileName = value; }
    public String getFileType() { return fileType; }
    public void setFileType(String value) { fileType = value; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long value) { fileSize = value; }
    public String getReturnDestination() { return returnDestination; }
    public void setReturnDestination(String value) { returnDestination = value; }
    public String getTransferReference() { return transferReference; }
    public void setTransferReference(String value) { transferReference = value; }
    public LocalDate getTransferredOn() { return transferredOn; }
    public void setTransferredOn(LocalDate value) { transferredOn = value; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long value) { reviewedBy = value; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime value) { reviewedAt = value; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String value) { reviewNote = value; }
    public String getReceiptReference() { return receiptReference; }
    public void setReceiptReference(String value) { receiptReference = value; }
    public String getReceiptKey() { return receiptKey; }
    public void setReceiptKey(String value) { receiptKey = value; }
}

