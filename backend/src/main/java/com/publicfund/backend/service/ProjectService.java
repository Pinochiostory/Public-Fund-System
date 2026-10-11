package com.publicfund.backend.service;

import com.publicfund.backend.entity.FundingApplication;
import com.publicfund.backend.entity.Project;
import com.publicfund.backend.entity.ProjectPhase;

import com.publicfund.backend.repository.FundingApplicationRepository;
import com.publicfund.backend.repository.ProjectPhaseRepository;
import com.publicfund.backend.repository.ProjectRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
@Service
public class ProjectService {
    private final WorkflowLocks locks;
    private final ReconciliationService reconciliation;
    private final com.publicfund.backend.repository.DisbursementRepository payments;
    private final com.publicfund.backend.repository.PhaseEvidenceRepository evidence;
    private final com.publicfund.backend.security.ProjectAccess access;

    private final ProjectRepository projectRepository;
    private final FundingApplicationRepository applicationRepository;
    private final ProjectPhaseRepository projectPhaseRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public ProjectService(ReconciliationService reconciliation, com.publicfund.backend.repository.DisbursementRepository payments, com.publicfund.backend.repository.PhaseEvidenceRepository evidence, com.publicfund.backend.security.ProjectAccess access, WorkflowLocks locks, 
            ProjectRepository projectRepository,
            FundingApplicationRepository applicationRepository,
            ProjectPhaseRepository projectPhaseRepository,
            AuditLogService auditLogService,
            NotificationService notificationService) {
        this.reconciliation = reconciliation;
        this.locks = locks; this.payments=payments; this.evidence=evidence; this.access=access;

        this.projectRepository = projectRepository;
        this.applicationRepository = applicationRepository;
        this.projectPhaseRepository = projectPhaseRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    /*
     * ============================================================
     * ADMIN CREATES PROJECT FROM APPROVED APPLICATION
     * ============================================================
     */
    public Project createProject(
            Long applicationId) {

        FundingApplication application =
                applicationRepository
                        .findByIdForUpdate(applicationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Funding application not found"
                                ));

        if (!"APPROVED".equals(
                application.getStatus())) {

            throw new IllegalArgumentException(
                    "Only approved applications can become projects"
            );
        }

        if (projectRepository
                .findByApplicationId(applicationId)
                .isPresent()) {

            throw new IllegalArgumentException(
                    "A project already exists for this application"
            );
        }

        Project project =
                new Project(
                        application.getId(),
                        application.getApplicantId(),
                        application.getTitle(),
                        application.getDescription(),
                        application.getRequestedBudget(),
                        application.getTimeline()
                );

        project.setExpectedCompletionDate(application.getExpectedCompletionDate());
        Project savedProject =
                projectRepository.save(
                        project
                );

        notificationService.createNotification(
                savedProject.getApplicantId(),
                "Project Created",
                "Your approved funding application \""
                        + savedProject.getTitle()
                        + "\" has been converted into an active project. "
                        + "You may now submit project phase proposals."
        );

        auditLogService.logProjectAction(project.getId(),
                "PROJECT_CREATED",
                "Project created from application ID: "
                        + applicationId
                        + " | Project ID: "
                        + savedProject.getId()
        );

        return savedProject;
    }

    /*
     * ============================================================
     * SERVICE PROVIDER REQUESTS PROJECT COMPLETION
     * ============================================================
     *
     * This does NOT formally complete the project.
     *
     * All non-rejected phases must already be COMPLETED.
     */
    public Project requestProjectCompletion(
            Long projectId,
            Long applicantId) {

        Project project =
                locks.project(projectId);
        WorkflowLocks.checkVersion(project.getVersion());

        /*
         * Prevent one provider from requesting completion
         * for another provider's project.
         */
        if (!project.getApplicantId()
                .equals(applicantId)) {

            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not have permission to perform this action."
            );
        }

        if ("COMPLETED".equals(
                project.getStatus())) {

            throw new IllegalArgumentException(
                    "Project is already completed"
            );
        }

        if ("PENDING_COMPLETION".equals(
                project.getStatus())) {

            throw new IllegalArgumentException(
                    "Project completion has already been requested"
            );
        }

        if (!"ACTIVE".equals(
                project.getStatus())) {

            throw new IllegalArgumentException(
                    "Only an active project can request completion"
            );
        }

        List<ProjectPhase> phases =
                projectPhaseRepository
                        .findByProjectId(
                                projectId
                        );

        if (phases.isEmpty()) {

            throw new IllegalArgumentException(
                    "Project must have at least one phase before completion can be requested"
            );
        }

        boolean hasValidPhase = false;

        for (ProjectPhase phase : phases) {

            /*
             * Rejected proposals are ignored because they
             * never became active project phases.
             */
            if ("REJECTED".equals(
                    phase.getStatus())) {

                continue;
            }

            hasValidPhase = true;

            if (!"COMPLETED".equals(
                    phase.getStatus())) {

                throw new IllegalArgumentException(
                        "All project phases must be completed before project completion can be requested"
                );
            }
        }

        if (!hasValidPhase) {

            throw new IllegalArgumentException(
                    "Project must have at least one completed phase before completion can be requested"
            );
        }

        project.setStatus(
                "PENDING_COMPLETION"
        );

        project.setCompletionRequestedAt(
                LocalDateTime.now()
        );

        Project savedProject =
                projectRepository.save(
                        project
                );

        auditLogService.logProjectAction(project.getId(),
                "PROJECT_COMPLETION_REQUESTED",
                "Project completion requested. Project ID: "
                        + projectId
        );

        return savedProject;
    }

    /*
     * ============================================================
     * ADMIN FORMALLY COMPLETES PROJECT
     * ============================================================
     */
    public Project completeProject(
            Long projectId) {

        Project project =
                locks.project(projectId);
        WorkflowLocks.checkVersion(project.getVersion());

        if ("COMPLETED".equals(
                project.getStatus())) {

            throw new IllegalArgumentException(
                    "Project is already completed"
            );
        }

        if (!"PENDING_COMPLETION".equals(
                project.getStatus())) {

            throw new IllegalArgumentException(
                    "The Service Provider must request project completion before the Administrator can conclude the project"
            );
        }

        List<ProjectPhase> phases =
                projectPhaseRepository
                        .findByProjectId(
                                projectId
                        );

        if (phases.isEmpty()) {

            throw new IllegalArgumentException(
                    "Project has no phases"
            );
        }

        boolean hasValidPhase = false;

        for (ProjectPhase phase : phases) {

            if ("REJECTED".equals(
                    phase.getStatus())) {

                continue;
            }

            hasValidPhase = true;

            if (!"COMPLETED".equals(
                    phase.getStatus())) {

                throw new IllegalArgumentException(
                        "All project phases must be completed before the project can be formally concluded"
                );
            }
        }

        if (!hasValidPhase) {

            throw new IllegalArgumentException(
                    "Project must contain at least one completed phase"
            );
        }

        Long completingAdmin = access.requireAdministratorId();
        var projectPayments = payments.findByProjectId(projectId);
        if (projectPayments.stream().anyMatch(p -> "PENDING".equals(p.getStatus()) || "PROCESSING".equals(p.getStatus())))
            throw new IllegalArgumentException("Resolve pending payments before closing this project. No pending payment may be hidden by closeout.");
        java.math.BigDecimal released = projectPayments.stream()
                .filter(p -> "RELEASED".equals(p.getStatus())).map(com.publicfund.backend.entity.Disbursement::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal remaining = project.getApprovedBudget().subtract(released);
        if (remaining.signum() < 0)
            throw new IllegalArgumentException("Recorded payments exceed the approved budget. Resolve the financial discrepancy before closing the project.");
        reconciliation.requireReconciled(project);
        if (project.getCloseoutRetainedAmount() != null)
            throw new IllegalArgumentException("A closeout snapshot already exists. It cannot be overwritten.");
        project.setCloseoutApprovedBudget(project.getApprovedBudget());
        project.setCloseoutReleasedAmount(released);
        project.setCloseoutRetainedAmount(remaining);
        project.setCloseoutInitiatedBy(completingAdmin);
        if (project.getCompletedAt()!=null) throw new IllegalArgumentException("Project completion time is already recorded.");
        project.setStatus("COMPLETED");

        project.setCompletedAt(
                LocalDateTime.now()
        );

        Project savedProject =
                projectRepository.save(
                        project
                );

        notificationService.createNotification(
                savedProject.getApplicantId(),
                "Project Completed",
                "Your project \""
                        + savedProject.getTitle()
                        + "\" has been formally concluded by the Administrator."
        );

        if (remaining.signum()>0) {
            notificationService.alertAdministrators("Completed project with remaining funds",
                    "Project " + project.getTitle() + " completed — R" + remaining + " undisbursed. Allocation frozen with the funder; an independent Administrator must review the closeout.");
        }
        auditLogService.logProjectAction(project.getId(),
                "PROJECT_COMPLETED",
                "Administrator formally completed project ID: "
                        + projectId + " | Approved: R" + project.getApprovedBudget() + " | Released: R" + released
                        + " | Frozen undisbursed allocation: R" + remaining
        );

        return savedProject;
    }

    /*
     * ============================================================
     * GET PROJECT
     * ============================================================
     */

    public Project reviewCloseout(Long id, String reference, String note) {
        Long reviewer = access.requireAdministratorId();
        Project project = locks.project(id);
        WorkflowLocks.checkVersion(project.getVersion());
        if (!"COMPLETED".equals(project.getStatus()) || project.getCloseoutRetainedAmount() == null)
            throw new IllegalArgumentException("This project has no recorded closeout snapshot to review.");
        if (project.getCloseoutRetainedAmount().signum() <= 0)
            throw new IllegalArgumentException("There is no undisbursed allocation requiring retention review.");
        if (project.getCloseoutReviewedAt() != null)
            throw new IllegalArgumentException("This closeout has already been reviewed. Its record cannot be overwritten.");
        if (reviewer.equals(project.getCloseoutInitiatedBy()))
            throw new org.springframework.security.access.AccessDeniedException("A different Administrator must independently review this closeout.");
        if (reference == null || reference.isBlank() || reference.trim().length() > 100)
            throw new IllegalArgumentException("Provide a funder reconciliation reference of 1 to 100 characters.");
        if (note == null || note.isBlank() || note.trim().length() > 1000)
            throw new IllegalArgumentException("Provide a reconciliation explanation of 1 to 1000 characters.");
        var released = payments.findByProjectId(id).stream().filter(p -> "RELEASED".equals(p.getStatus()))
                .map(com.publicfund.backend.entity.Disbursement::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        if (released.compareTo(project.getCloseoutReleasedAmount()) != 0
                || project.getApprovedBudget().compareTo(project.getCloseoutApprovedBudget()) != 0)
            throw new IllegalArgumentException("Financial records differ from the frozen closeout snapshot. Reconciliation requires investigation.");
        project.setCloseoutReviewedBy(reviewer);
        project.setCloseoutReviewedAt(LocalDateTime.now());
        project.setCloseoutReference(reference.trim());
        project.setCloseoutNote(note.trim());
        auditLogService.logProjectAction(id, "PROJECT_CLOSEOUT_REVIEWED",
                "Independent review: R" + project.getCloseoutRetainedAmount()
                + " undisbursed allocation retained with funder. No payment authorized. Reference: "
                + reference.trim() + " | Reason: " + note.trim());
        notificationService.createNotification(project.getApplicantId(), "Project balance review recorded",
                "The undisbursed allocation for " + project.getTitle() + " remains frozen with the funder. No additional payment was authorized.");
        return projectRepository.save(project);
    }

    public java.util.Map<String,Object> summary(Long id) {
        Project project=getProjectById(id);
        access.requireReader(project);
        var phases=projectPhaseRepository.findByProjectIdOrderByPhaseOrderAsc(id);
        var disbursements=payments.findByProjectId(id);
        var released=disbursements.stream().filter(p -> "RELEASED".equals(p.getStatus()))
                .map(com.publicfund.backend.entity.Disbursement::getAmount)
                .reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
        var valid=phases.stream().filter(p -> !"REJECTED".equals(p.getStatus())).toList();
        long completed=valid.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count();
        var result=new java.util.LinkedHashMap<String,Object>();
        result.put("project",project); result.put("phases",phases); result.put("payments",disbursements);
        result.put("evidence",phases.stream().flatMap(p -> evidence.findByPhaseId(p.getId()).stream()).toList());
        result.put("totalFundsReleased",released);
        result.put("remainingBalance",project.getApprovedBudget().subtract(released));
        result.put("completedPhases",completed);result.put("outstandingPhases",valid.size()-completed);
        result.put("startedAt",phases.stream().map(ProjectPhase::getStartedAt).filter(java.util.Objects::nonNull).min(java.time.LocalDateTime::compareTo).orElse(null));
        return result;
    }

    public java.util.List<com.publicfund.backend.entity.AuditLog> activity(Long id) {
        Project project=getProjectById(id);
        access.requireReader(project);
        return auditLogService.projectActivity(id,project.getApplicationId());
    }

    public Project getProjectById(
            Long id) {

        return projectRepository
                .findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Project not found"
                        ));
    }

    /*
     * ============================================================
     * GET PROJECT BY APPLICATION
     * ============================================================
     */
    public Project getProjectByApplicationId(
            Long applicationId) {

        return projectRepository
                .findByApplicationId(
                        applicationId
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Project not found for this application"
                        ));
    }

    /*
     * ============================================================
     * GET PROVIDER PROJECTS
     * ============================================================
     */
    public List<Project> getProjectsByApplicant(
            Long applicantId) {

        return projectRepository
                .findByApplicantId(
                        applicantId
                );
    }

    /*
     * ============================================================
     * GET ALL PROJECTS
     * ============================================================
     */
    public List<Project> getAllProjects() {

        return projectRepository
                .findAll();
    }

    /*
     * ============================================================
     * GET PROJECTS BY STATUS
     * ============================================================
     */
    public List<Project> getProjectsByStatus(
            String status) {

        if (status == null ||
                status.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Project status is required"
            );
        }

        return projectRepository
                .findByStatus(
                        status
                                .trim()
                                .toUpperCase()
                );
    }
}
