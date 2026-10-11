package com.publicfund.backend.service;

import com.publicfund.backend.entity.Disbursement;
import com.publicfund.backend.entity.Project;
import com.publicfund.backend.entity.ProjectPhase;
import com.publicfund.backend.repository.DisbursementRepository;
import com.publicfund.backend.repository.ProjectPhaseRepository;
import com.publicfund.backend.repository.ProjectRepository;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
@Service
public class ProjectPhaseService {
    private final WorkflowLocks locks;
    private final com.publicfund.backend.repository.PhaseEvidenceRepository evidence;
    private final com.publicfund.backend.security.ProjectAccess access;

    private final ProjectPhaseRepository phaseRepository;
    private final ProjectRepository projectRepository;
    private final DisbursementRepository disbursementRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public ProjectPhaseService(com.publicfund.backend.repository.PhaseEvidenceRepository evidence, WorkflowLocks locks, com.publicfund.backend.security.ProjectAccess access, 
            ProjectPhaseRepository phaseRepository,
            ProjectRepository projectRepository,
            DisbursementRepository disbursementRepository,
            AuditLogService auditLogService,
            NotificationService notificationService) {
        this.locks = locks; this.evidence=evidence;
        this.access = access;

        this.phaseRepository = phaseRepository;
        this.projectRepository = projectRepository;
        this.disbursementRepository = disbursementRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    /*
     * SERVICE PROVIDER CREATES A PHASE PROPOSAL
     */
    public ProjectPhase createPhase(
            Long projectId,
            String phaseName,
            String description,
            BigDecimal allocatedBudget,
            Integer phaseOrder, java.time.LocalDate deadline) {

        Project project =
                locks.project(projectId);
        access.requireOwner(project);
        WorkflowLocks.requireActive(project);

        if ("COMPLETED".equals(project.getStatus())) {
            throw new IllegalArgumentException(
                    "Cannot create a phase for a completed project"
            );
        }

        validatePhaseInput(
                phaseName,
                description,
                allocatedBudget,
                phaseOrder
        );

        if (deadline != null && deadline.isBefore(java.time.LocalDate.now()))
            throw new IllegalArgumentException("Phase deadline cannot be in the past.");
        if (deadline != null && project.getExpectedCompletionDate()!=null && deadline.isAfter(project.getExpectedCompletionDate()))
            throw new IllegalArgumentException("Phase deadline cannot be after the project's expected completion date.");
        List<ProjectPhase> existingPhases =
                phaseRepository.findByProjectId(projectId);

        if (existingPhases.stream().anyMatch(p -> java.util.Set.of("FUNDED","IN_PROGRESS","EVIDENCE_APPROVED").contains(p.getStatus())))
            throw new IllegalArgumentException("The active phase must be formally completed before another phase may be proposed.");
        int nextOrder=existingPhases.stream().filter(p -> !"REJECTED".equals(p.getStatus())).mapToInt(ProjectPhase::getPhaseOrder).max().orElse(0)+1;
        if (phaseOrder != nextOrder) throw new IllegalArgumentException("The next phase must have order " + nextOrder + ".");
        for (ProjectPhase existingPhase : existingPhases) {

            if (existingPhase.getPhaseOrder()
                    .equals(phaseOrder)
                    && !"REJECTED".equals(
                            existingPhase.getStatus()
                    )) {

                throw new IllegalArgumentException(
                        "A phase with this order already exists"
                );
            }
        }

        BigDecimal totalRequestedBudget =
                BigDecimal.ZERO;

        for (ProjectPhase existingPhase : existingPhases) {

            if (!"REJECTED".equals(
                    existingPhase.getStatus()
            )) {

                totalRequestedBudget =
                        totalRequestedBudget.add(
                                existingPhase
                                        .getAllocatedBudget()
                        );
            }
        }

        BigDecimal newTotal =
                totalRequestedBudget.add(
                        allocatedBudget
                );

        if (newTotal.compareTo(
                project.getApprovedBudget()) > 0) {

            notificationService.alertAfterRejectedAction("Insufficient project funds",
                    "Project " + project.getTitle() + ": requested phase " + phaseName + " exceeds available allocation by R" + newTotal.subtract(project.getApprovedBudget()) + ". Administrative review required; budget unchanged.");
            throw new IllegalArgumentException(
                    "Requested amount exceeds the R" + project.getApprovedBudget().subtract(totalRequestedBudget) + " available for phase allocation. An Administrator must review the shortfall."
            );
        }

        ProjectPhase phase =
                new ProjectPhase(
                        projectId,
                        phaseName.trim(),
                        description.trim(),
                        allocatedBudget,
                        phaseOrder
                );

        phase.setDeadline(deadline);
        phase.setStatus(
                "PENDING_APPROVAL"
        );

        ProjectPhase savedPhase =
                phaseRepository.save(phase);

        auditLogService.logProjectAction(phase.getProjectId(),
                "PHASE_SUBMITTED",
                "Phase proposal submitted. Phase ID: "
                        + savedPhase.getId()
                        + " | Project ID: "
                        + projectId
        );

        return savedPhase;
    }

    /*
     * ADMIN APPROVES PHASE PROPOSAL
     */
    public ProjectPhase approvePhase(
            Long phaseId) {

        ProjectPhase phase =
                locks.phase(phaseId);
        WorkflowLocks.checkVersion(phase.getVersion());
        WorkflowLocks.requireActive(getProject(phase.getProjectId()));

        if (!"PENDING_APPROVAL".equals(
                phase.getStatus())) {

            throw new IllegalArgumentException(
                    "Only phases awaiting approval can be approved"
            );
        }

        phase.setStatus("APPROVED");
        phase.setReviewedBy(com.publicfund.backend.security.ProjectAccess.actor());
        phase.setRejectionReason(null);

        phase.setApprovedAt(
                LocalDateTime.now()
        );

        ProjectPhase savedPhase =
                phaseRepository.save(phase);

        Project project =
                getProject(
                        phase.getProjectId()
                );

        notificationService.createNotification(
                project.getApplicantId(),
                "Phase Approved",
                "Your phase \""
                        + phase.getPhaseName()
                        + "\" has been approved. Funding can now be released."
        );

        auditLogService.logProjectAction(phase.getProjectId(),
                "PHASE_APPROVED",
                "Phase approved. Phase ID: "
                        + phaseId
        );

        return savedPhase;
    }

    /*
     * ADMIN REJECTS PHASE PROPOSAL
     */
    public ProjectPhase rejectPhase(
            Long phaseId,
            String rejectionReason) {

        ProjectPhase phase =
                locks.phase(phaseId);
        WorkflowLocks.checkVersion(phase.getVersion());
        WorkflowLocks.requireActive(getProject(phase.getProjectId()));

        if (!"PENDING_APPROVAL".equals(
                phase.getStatus())) {

            throw new IllegalArgumentException(
                    "Only phases awaiting approval can be rejected"
            );
        }

        if (rejectionReason == null ||
                rejectionReason.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "A rejection reason is required"
            );
        }

        phase.setStatus("REJECTED");
        phase.setRejectedAt(LocalDateTime.now());
        phase.setReviewedBy(com.publicfund.backend.security.ProjectAccess.actor());

        phase.setRejectionReason(
                rejectionReason.trim()
        );

        ProjectPhase savedPhase =
                phaseRepository.save(phase);

        Project project =
                getProject(
                        phase.getProjectId()
                );

        notificationService.createNotification(
                project.getApplicantId(),
                "Phase Rejected",
                "Your phase \""
                        + phase.getPhaseName()
                        + "\" was rejected. Reason: "
                        + rejectionReason.trim()
        );

        auditLogService.logProjectAction(phase.getProjectId(),
                "PHASE_REJECTED",
                "Phase rejected. Phase ID: "
                        + phaseId
                        + " | Reason: "
                        + rejectionReason.trim()
        );

        return savedPhase;
    }

    /*
     * CALLED AFTER ADMIN RELEASES THE PHASE FUNDS
     */
    public ProjectPhase markPhaseFunded(
            Long phaseId) {

        ProjectPhase phase =
                locks.phase(phaseId);
        WorkflowLocks.checkVersion(phase.getVersion());
        WorkflowLocks.requireActive(getProject(phase.getProjectId()));

        if (!"APPROVED".equals(
                phase.getStatus())) {

            throw new IllegalArgumentException(
                    "Only an approved phase can be funded"
            );
        }

        List<Disbursement> disbursements =
                disbursementRepository
                        .findByPhaseId(phaseId);

        boolean released = false;

        for (Disbursement disbursement :
                disbursements) {

            if ("RELEASED".equals(
                    disbursement.getStatus())) {

                released = true;
                break;
            }
        }

        if (!released) {
            throw new IllegalArgumentException(
                    "Phase funds have not been released"
            );
        }

        phase.setStatus("FUNDED");

        phase.setFundedAt(
                LocalDateTime.now()
        );

        ProjectPhase savedPhase =
                phaseRepository.save(phase);

        Project project =
                getProject(
                        phase.getProjectId()
                );

        notificationService.createNotification(
                project.getApplicantId(),
                "Phase Funds Released",
                "Funds for phase \""
                        + phase.getPhaseName()
                        + "\" have been released. You may begin the phase when eligible."
        );

        auditLogService.logProjectAction(phase.getProjectId(),
                "PHASE_FUNDED",
                "Phase marked as funded. Phase ID: "
                        + phaseId
        );

        return savedPhase;
    }

    /*
     * SERVICE PROVIDER STARTS WORK ON THE FUNDED PHASE
     */
    public ProjectPhase startPhase(
            Long phaseId) {

        ProjectPhase phase =
                locks.phase(phaseId);
        access.requireOwner(getProject(phase.getProjectId()));
        WorkflowLocks.checkVersion(phase.getVersion());
        WorkflowLocks.requireActive(getProject(phase.getProjectId()));

        if (!"FUNDED".equals(
                phase.getStatus())) {

            throw new IllegalArgumentException(
                    "Only a funded phase can be started"
            );
        }

        List<ProjectPhase> projectPhases =
                phaseRepository.findByProjectId(
                        phase.getProjectId()
                );

        /*
         * ABSOLUTE RULE:
         *
         * NO OTHER PHASE MAY START WHILE ANOTHER PHASE
         * IS STILL ACTIVE.
         *
         * IN_PROGRESS means work is still ongoing.
         *
         * EVIDENCE_APPROVED means verification has passed,
         * but the Administrator has not formally concluded
         * the phase yet.
         *
         * Both statuses therefore block the next phase.
         */
        for (ProjectPhase projectPhase :
                projectPhases) {

            if (projectPhase.getId()
                    .equals(phase.getId())) {

                continue;
            }

            String status =
                    projectPhase.getStatus();

            if ("IN_PROGRESS".equals(status)
                    || "EVIDENCE_APPROVED".equals(status)) {

                throw new IllegalArgumentException(
                        "Another phase is still active. "
                                + "It must be formally completed before another phase can begin."
                );
            }
        }

        // Check every predecessor, including inconsistent legacy sequences.
        // The project row lock serializes concurrent starts for this project.
        for (ProjectPhase predecessor : projectPhases) {
            if (predecessor.getPhaseOrder() < phase.getPhaseOrder()
                    && !"REJECTED".equals(predecessor.getStatus())
                    && !"COMPLETED".equals(predecessor.getStatus())) {
                throw new IllegalArgumentException("Phase " + predecessor.getPhaseOrder()
                        + " must be formally completed before this phase can begin.");
            }
        }

        phase.setStatus(
                "IN_PROGRESS"
        );

        if (phase.getStartedAt()!=null) throw new IllegalArgumentException("This phase already has a start timestamp. It cannot be overwritten.");
        phase.setStartedAt(
                LocalDateTime.now()
        );

        ProjectPhase savedPhase =
                phaseRepository.save(phase);

        auditLogService.logProjectAction(phase.getProjectId(),
                "PHASE_STARTED",
                "Phase started. Phase ID: "
                        + phaseId
        );

        return savedPhase;
    }

    /*
     * CALLED AFTER VERIFICATION OFFICER APPROVES
     * THE EVIDENCE.
     *
     * IMPORTANT:
     * THIS DOES NOT COMPLETE THE PHASE.
     */
    public ProjectPhase markEvidenceApproved(
            Long phaseId) {

        ProjectPhase phase =
                locks.phase(phaseId);
        WorkflowLocks.checkVersion(phase.getVersion());
        WorkflowLocks.requireActive(getProject(phase.getProjectId()));

        if (!"IN_PROGRESS".equals(
                phase.getStatus())) {

            throw new IllegalArgumentException(
                    "Evidence can only be approved for a phase that is in progress"
            );
        }

        if (!EvidenceSubmissions.fullyApproved(evidence.findByPhaseId(phaseId)))
            throw new IllegalArgumentException("Every file in the latest evidence submission must be approved before verifying this phase.");
        phase.setStatus("EVIDENCE_APPROVED");

        ProjectPhase savedPhase =
                phaseRepository.save(phase);

        auditLogService.logProjectAction(phase.getProjectId(),
                "PHASE_EVIDENCE_APPROVED",
                "Evidence approved for phase ID: "
                        + phaseId
                        + ". Awaiting administrator conclusion."
        );

        return savedPhase;
    }

    /*
     * ADMIN FORMALLY COMPLETES THE PHASE
     */
    public ProjectPhase concludePhase(
            Long phaseId) {

        ProjectPhase phase =
                locks.phase(phaseId);
        WorkflowLocks.checkVersion(phase.getVersion());
        WorkflowLocks.requireActive(getProject(phase.getProjectId()));

        if (!"EVIDENCE_APPROVED".equals(
                phase.getStatus())) {

            throw new IllegalArgumentException(
                    "The phase can only be concluded after its evidence has been approved"
            );
        }

        if (!EvidenceSubmissions.fullyApproved(evidence.findByPhaseId(phaseId)))
            throw new IllegalArgumentException("Every file in the latest evidence submission must be approved before completing this phase.");
        if (phase.getStartedAt() == null || phase.getCompletedAt() != null) {
            throw new IllegalArgumentException("Only a started phase without a completion timestamp can be completed.");
        }
        phase.setStatus("COMPLETED");

        phase.setCompletedAt(
                LocalDateTime.now()
        );

        ProjectPhase savedPhase =
                phaseRepository.save(phase);

        Project project =
                getProject(
                        phase.getProjectId()
                );

        notificationService.createNotification(
                project.getApplicantId(),
                "Phase Completed",
                "Phase \""
                        + phase.getPhaseName()
                        + "\" has been formally completed. You may proceed with the next phase."
        );

        auditLogService.logProjectAction(phase.getProjectId(),
                "PHASE_COMPLETED",
                "Administrator concluded phase ID: "
                        + phaseId
        );

        return savedPhase;
    }

    public ProjectPhase getPhaseById(
            Long id) {

        return phaseRepository
                .findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Project phase not found"
                        ));
    }

    public List<ProjectPhase> getPhasesByProject(
            Long projectId) {

        return phaseRepository
                .findByProjectIdOrderByPhaseOrderAsc(
                        projectId
                );
    }

    public List<ProjectPhase> getPhasesByApplicant(
            Long applicantId) {

        List<Project> projects =
                projectRepository
                        .findByApplicantId(
                                applicantId
                        );

        List<ProjectPhase> phases =
                new ArrayList<>();

        for (Project project : projects) {

            phases.addAll(
                    phaseRepository
                            .findByProjectIdOrderByPhaseOrderAsc(
                                    project.getId()
                            )
            );
        }

        return phases;
    }

    public List<ProjectPhase> getPhasesByStatus(
            String status) {

        if (status == null ||
                status.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Phase status is required"
            );
        }

        return phaseRepository.findByStatus(
                status.trim().toUpperCase()
        );
    }

    public List<ProjectPhase> getAllPhases() {
        List<ProjectPhase> phases = phaseRepository.findAll();
        java.util.Map<Long, String> titles = new java.util.HashMap<>();
        projectRepository.findAllById(phases.stream().map(ProjectPhase::getProjectId).distinct().toList())
                .forEach(project -> titles.put(project.getId(), project.getTitle()));
        phases.forEach(phase -> phase.setProjectTitle(titles.get(phase.getProjectId())));
        return phases;
    }

    /*
     * HELPER METHODS
     */

    private Project getProject(
            Long projectId) {

        return projectRepository
                .findById(projectId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Project not found"
                        ));
    }

    private void validatePhaseInput(
            String phaseName,
            String description,
            BigDecimal allocatedBudget,
            Integer phaseOrder) {

        if (phaseName == null ||
                phaseName.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Phase name is required"
            );
        }

        if (description == null ||
                description.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Phase description is required"
            );
        }

        if (allocatedBudget == null ||
                allocatedBudget.compareTo(
                        BigDecimal.ZERO
                ) <= 0) {

            throw new IllegalArgumentException(
                    "Phase budget must be greater than zero"
            );
        }

        if (allocatedBudget.scale()>2 || allocatedBudget.precision()>15 || phaseName.trim().length()>255 || description.length()>10000)
            throw new IllegalArgumentException("Use a phase name under 256 characters, description under 10,001 characters and an amount with at most two decimal places.");
        if (phaseOrder == null ||
                phaseOrder <= 0) {

            throw new IllegalArgumentException(
                    "Phase order must be greater than zero"
            );
        }
    }
}
