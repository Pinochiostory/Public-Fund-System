package com.publicfund.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "funding_applications")
public class FundingApplication {
    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private String rejectionReason;
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String value) { this.rejectionReason = value; }

    private java.time.LocalDateTime rejectedAt;
    public java.time.LocalDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(java.time.LocalDateTime value) { this.rejectedAt = value; }

    private String rejectedBy;
    public String getRejectedBy() { return rejectedBy; }
    public void setRejectedBy(String value) { this.rejectedBy = value; }

    private java.time.LocalDate expectedCompletionDate;
    public java.time.LocalDate getExpectedCompletionDate() { return expectedCompletionDate; }
    public void setExpectedCompletionDate(java.time.LocalDate value) { this.expectedCompletionDate = value; }

    @jakarta.persistence.Version
    @jakarta.persistence.Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;
    public long getVersion() { return version; }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long applicantId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(
        nullable = false,
        precision = 15,
        scale = 2
    )
    private BigDecimal requestedBudget;

    @Column(nullable = false)
    private String timeline;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    public FundingApplication() {
    }

    public FundingApplication(
            Long applicantId,
            String title,
            String description,
            BigDecimal requestedBudget,
            String timeline) {

        this.applicantId = applicantId;
        this.title = title;
        this.description = description;
        this.requestedBudget = requestedBudget;
        this.timeline = timeline;
        this.status = "PENDING";
        this.submittedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {

        if (submittedAt == null) {
            submittedAt = LocalDateTime.now();
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

    public Long getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(Long applicantId) {
        this.applicantId = applicantId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(
            String description) {

        this.description = description;
    }

    public BigDecimal getRequestedBudget() {
        return requestedBudget;
    }

    public void setRequestedBudget(
            BigDecimal requestedBudget) {

        this.requestedBudget = requestedBudget;
    }

    public String getTimeline() {
        return timeline;
    }

    public void setTimeline(
            String timeline) {

        this.timeline = timeline;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(
            String status) {

        this.status = status;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(
            LocalDateTime submittedAt) {

        this.submittedAt = submittedAt;
    }
}
