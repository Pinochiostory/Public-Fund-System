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
@Table(name = "projects")
public class Project {
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

    @Column(nullable = false, unique = true)
    private Long applicationId;

    @Column(nullable = false)
    private Long applicantId;

    @Column(nullable = false)
    private String title;

    @Column(
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String description;

    @Column(
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal approvedBudget;

    @Column(nullable = false)
    private String timeline;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime completionRequestedAt;

    @Column
    private LocalDateTime completedAt;

    // Immutable-at-service-level closeout snapshot. Null means legacy/unreconciled.
    @Column(precision = 15, scale = 2)
    private BigDecimal closeoutApprovedBudget;
    @Column(precision = 15, scale = 2)
    private BigDecimal closeoutReleasedAmount;
    @Column(precision = 15, scale = 2)
    private BigDecimal closeoutRetainedAmount;
    private Long closeoutInitiatedBy;
    private Long closeoutReviewedBy;
    private LocalDateTime closeoutReviewedAt;
    @Column(length = 100)
    private String closeoutReference;
    @Column(length = 1000)
    private String closeoutNote;

    public BigDecimal getCloseoutApprovedBudget() { return closeoutApprovedBudget; }
    public void setCloseoutApprovedBudget(BigDecimal value) { closeoutApprovedBudget = value; }
    public BigDecimal getCloseoutReleasedAmount() { return closeoutReleasedAmount; }
    public void setCloseoutReleasedAmount(BigDecimal value) { closeoutReleasedAmount = value; }
    public BigDecimal getCloseoutRetainedAmount() { return closeoutRetainedAmount; }
    public void setCloseoutRetainedAmount(BigDecimal value) { closeoutRetainedAmount = value; }
    public Long getCloseoutInitiatedBy() { return closeoutInitiatedBy; }
    public void setCloseoutInitiatedBy(Long value) { closeoutInitiatedBy = value; }
    public Long getCloseoutReviewedBy() { return closeoutReviewedBy; }
    public void setCloseoutReviewedBy(Long value) { closeoutReviewedBy = value; }
    public LocalDateTime getCloseoutReviewedAt() { return closeoutReviewedAt; }
    public void setCloseoutReviewedAt(LocalDateTime value) { closeoutReviewedAt = value; }
    public String getCloseoutReference() { return closeoutReference; }
    public void setCloseoutReference(String value) { closeoutReference = value; }
    public String getCloseoutNote() { return closeoutNote; }
    public void setCloseoutNote(String value) { closeoutNote = value; }

    public Project() {
    }

    public Project(
            Long applicationId,
            Long applicantId,
            String title,
            String description,
            BigDecimal approvedBudget,
            String timeline) {

        this.applicationId =
                applicationId;

        this.applicantId =
                applicantId;

        this.title =
                title;

        this.description =
                description;

        this.approvedBudget =
                approvedBudget;

        this.timeline =
                timeline;

        this.status =
                "ACTIVE";

        this.createdAt =
                LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {

            createdAt =
                    LocalDateTime.now();
        }

        if (status == null ||
                status.trim().isEmpty()) {

            status =
                    "ACTIVE";
        }
    }

    public Long getId() {

        return id;
    }

    public void setId(
            Long id) {

        this.id = id;
    }

    public Long getApplicationId() {

        return applicationId;
    }

    public void setApplicationId(
            Long applicationId) {

        this.applicationId =
                applicationId;
    }

    public Long getApplicantId() {

        return applicantId;
    }

    public void setApplicantId(
            Long applicantId) {

        this.applicantId =
                applicantId;
    }

    public String getTitle() {

        return title;
    }

    public void setTitle(
            String title) {

        this.title =
                title;
    }

    public String getDescription() {

        return description;
    }

    public void setDescription(
            String description) {

        this.description =
                description;
    }

    public BigDecimal getApprovedBudget() {

        return approvedBudget;
    }

    public void setApprovedBudget(
            BigDecimal approvedBudget) {

        this.approvedBudget =
                approvedBudget;
    }

    public String getTimeline() {

        return timeline;
    }

    public void setTimeline(
            String timeline) {

        this.timeline =
                timeline;
    }

    public String getStatus() {

        return status;
    }

    public void setStatus(
            String status) {

        this.status =
                status;
    }

    public LocalDateTime getCreatedAt() {

        return createdAt;
    }

    public void setCreatedAt(
            LocalDateTime createdAt) {

        this.createdAt =
                createdAt;
    }

    public LocalDateTime getCompletionRequestedAt() {

        return completionRequestedAt;
    }

    public void setCompletionRequestedAt(
            LocalDateTime completionRequestedAt) {

        this.completionRequestedAt =
                completionRequestedAt;
    }

    public LocalDateTime getCompletedAt() {

        return completedAt;
    }

    public void setCompletedAt(
            LocalDateTime completedAt) {

        this.completedAt =
                completedAt;
    }
}
