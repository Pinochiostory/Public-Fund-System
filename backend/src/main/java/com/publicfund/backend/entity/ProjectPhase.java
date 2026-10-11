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
@Table(name = "project_phases")
public class ProjectPhase {
    private java.time.LocalDate deadline;
    public java.time.LocalDate getDeadline() { return deadline; }
    public void setDeadline(java.time.LocalDate value) { this.deadline = value; }

    private java.time.LocalDateTime rejectedAt;
    public java.time.LocalDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(java.time.LocalDateTime value) { this.rejectedAt = value; }

    private String reviewedBy;
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String value) { this.reviewedBy = value; }

    @jakarta.persistence.Version
    @jakarta.persistence.Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;
    public long getVersion() { return version; }

    @jakarta.persistence.Transient
    private String projectTitle;
    public String getProjectTitle() { return projectTitle; }
    public void setProjectTitle(String projectTitle) { this.projectTitle = projectTitle; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String phaseName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal allocatedBudget;

    @Column(nullable = false)
    private Integer phaseOrder;

    @Column(nullable = false)
    private String status = "PENDING_APPROVAL";

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime approvedAt;

    @Column
    private LocalDateTime fundedAt;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime completedAt;

    public ProjectPhase() {
    }

    public ProjectPhase(
            Long projectId,
            String phaseName,
            String description,
            BigDecimal allocatedBudget,
            Integer phaseOrder) {

        this.projectId = projectId;
        this.phaseName = phaseName;
        this.description = description;
        this.allocatedBudget = allocatedBudget;
        this.phaseOrder = phaseOrder;
        this.status = "PENDING_APPROVAL";
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (status == null ||
                status.trim().isEmpty()) {

            status = "PENDING_APPROVAL";
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(
            Long id) {

        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(
            Long projectId) {

        this.projectId = projectId;
    }

    public String getPhaseName() {
        return phaseName;
    }

    public void setPhaseName(
            String phaseName) {

        this.phaseName = phaseName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(
            String description) {

        this.description = description;
    }

    public BigDecimal getAllocatedBudget() {
        return allocatedBudget;
    }

    public void setAllocatedBudget(
            BigDecimal allocatedBudget) {

        this.allocatedBudget = allocatedBudget;
    }

    public Integer getPhaseOrder() {
        return phaseOrder;
    }

    public void setPhaseOrder(
            Integer phaseOrder) {

        this.phaseOrder = phaseOrder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(
            String status) {

        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(
            String rejectionReason) {

        this.rejectionReason =
                rejectionReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(
            LocalDateTime createdAt) {

        this.createdAt = createdAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(
            LocalDateTime approvedAt) {

        this.approvedAt = approvedAt;
    }

    public LocalDateTime getFundedAt() {
        return fundedAt;
    }

    public void setFundedAt(
            LocalDateTime fundedAt) {

        this.fundedAt = fundedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(
            LocalDateTime startedAt) {

        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(
            LocalDateTime completedAt) {

        this.completedAt = completedAt;
    }
}
