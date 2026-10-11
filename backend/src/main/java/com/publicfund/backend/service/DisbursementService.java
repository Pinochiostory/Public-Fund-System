package com.publicfund.backend.service;

import com.publicfund.backend.entity.Disbursement;
import com.publicfund.backend.entity.Project;
import com.publicfund.backend.entity.ProjectPhase;

import com.publicfund.backend.repository.DisbursementRepository;
import com.publicfund.backend.repository.ProjectPhaseRepository;
import com.publicfund.backend.repository.ProjectRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
@Service
public class DisbursementService {
    private final WorkflowLocks locks;
    private final FileStorageService files;
    private final com.publicfund.backend.security.ProjectAccess access;

    private final DisbursementRepository disbursementRepository;
    private final ProjectPhaseRepository phaseRepository;
    private final ProjectRepository projectRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public DisbursementService(FileStorageService files, com.publicfund.backend.security.ProjectAccess access, WorkflowLocks locks, 
            DisbursementRepository disbursementRepository,
            ProjectPhaseRepository phaseRepository,
            ProjectRepository projectRepository,
            AuditLogService auditLogService,
            NotificationService notificationService) {
        this.locks = locks; this.files=files; this.access=access;

        this.disbursementRepository =
                disbursementRepository;

        this.phaseRepository =
                phaseRepository;

        this.projectRepository =
                projectRepository;

        this.auditLogService =
                auditLogService;

        this.notificationService =
                notificationService;
    }

    /*
     * ============================================================
     * ADMIN CREATES DISBURSEMENT
     * ============================================================
     *
     * A disbursement can only be created after the
     * Administrator has approved the phase proposal.
     */
    public Disbursement createDisbursement(
            Long phaseId) {

        ProjectPhase phase = locks.phase(phaseId);
        WorkflowLocks.requireActive(getProject(phase.getProjectId()));

        if (!"APPROVED".equals(
                phase.getStatus())) {

            throw new IllegalArgumentException(
                    "A disbursement can only be created for an approved phase"
            );
        }

        List<Disbursement> existing =
                disbursementRepository
                        .findByPhaseId(
                                phaseId
                        );

        if (!existing.isEmpty()) {

            throw new IllegalArgumentException(
                    "A disbursement already exists for this phase"
            );
        }

        Disbursement disbursement =
                new Disbursement(
                        phase.getProjectId(),
                        phase.getId(),
                        phase.getAllocatedBudget()
                );

        Disbursement savedDisbursement =
                disbursementRepository.save(
                        disbursement
                );

        auditLogService.logProjectAction(phase.getProjectId(),
                "DISBURSEMENT_CREATED",
                "Disbursement created for phase ID: "
                        + phaseId
                        + " | Amount: R"
                        + savedDisbursement.getAmount()
        );

        return savedDisbursement;
    }

    /*
     * ============================================================
     * ADMIN RELEASES FUNDS
     * ============================================================
     *
     * When funds are released:
     *
     * Disbursement -> RELEASED
     * Phase        -> FUNDED
     *
     * The Provider may then start the phase, provided the
     * previous phase has been completed.
     */
    public Disbursement releaseDisbursement(Long disbursementId, String paymentReference) {

        Disbursement disbursement =
                locks.payment(disbursementId);
        WorkflowLocks.checkVersion(disbursement.getVersion());
        WorkflowLocks.requireActive(getProject(disbursement.getProjectId()));

        if (!java.util.Set.of("PENDING", "PROCESSING").contains(disbursement.getStatus())) {

            throw new IllegalArgumentException(
                    "Only pending disbursements can be released"
            );
        }

        ProjectPhase phase =
                getPhase(
                        disbursement.getPhaseId()
                );

        if (!"APPROVED".equals(
                phase.getStatus())) {

            throw new IllegalArgumentException(
                    "Funds can only be released for an approved phase"
            );
        }

        /*
         * Phase 2+ cannot receive funds until the
         * previous phase has been formally completed.
         */
        if (phase.getPhaseOrder() > 1) {

            Integer previousOrder =
                    phase.getPhaseOrder() - 1;

            List<ProjectPhase> projectPhases =
                    phaseRepository
                            .findByProjectId(
                                    phase.getProjectId()
                            );

            ProjectPhase previousPhase =
                    findPhaseByOrder(
                            projectPhases,
                            previousOrder
                    );

            if (previousPhase == null) {

                throw new IllegalArgumentException(
                        "Previous phase does not exist"
                );
            }

            if (!"COMPLETED".equals(
                    previousPhase.getStatus())) {

                throw new IllegalArgumentException(
                        "Previous phase must be completed before funds for this phase can be released"
                );
            }
        }

        /*
         * Release money.
         */
        BigDecimal released = disbursementRepository.findByProjectId(disbursement.getProjectId()).stream()
                .filter(d -> "RELEASED".equals(d.getStatus())).map(Disbursement::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = getProject(disbursement.getProjectId()).getApprovedBudget().subtract(released);
        if (disbursement.getAmount().signum() <= 0 || disbursement.getAmount().compareTo(phase.getAllocatedBudget()) != 0)
            throw new IllegalArgumentException("Payment amount does not match the approved phase amount. Contact an Administrator.");
        if (disbursement.getAmount().compareTo(remaining) > 0) {
            String message="Insufficient project funds. Remaining: R" + remaining + ". This payment requires an additional R" + disbursement.getAmount().subtract(remaining) + ".";
            notificationService.alertAfterRejectedAction("Insufficient project funds", "Project " + getProject(disbursement.getProjectId()).getTitle() + ": " + message);
            throw new IllegalArgumentException(message);
        }
        if (paymentReference == null || paymentReference.isBlank() || paymentReference.length()>100) throw new IllegalArgumentException("A payment reference between 1 and 100 characters is required.");
        disbursement.setPaymentReference(paymentReference.trim());
        disbursement.setStatus("RELEASED");
        disbursement.setRecordedBy(com.publicfund.backend.security.ProjectAccess.actor());

        disbursement.setReleasedAt(
                LocalDateTime.now()
        );

        Disbursement savedDisbursement =
                disbursementRepository.save(
                        disbursement
                );

        /*
         * Automatically mark phase as funded.
         *
         * No second Admin action is required.
         */
        phase.setStatus(
                "FUNDED"
        );

        phase.setFundedAt(
                LocalDateTime.now()
        );

        phaseRepository.save(
                phase
        );

        Project project =
                getProject(
                        phase.getProjectId()
                );

        notificationService.createNotification(
                project.getApplicantId(),
                "Funds Released",
                "R"
                        + savedDisbursement.getAmount()
                        + " has been released for phase \""
                        + phase.getPhaseName()
                        + "\". The phase is now funded and may be started when eligible."
        );

        auditLogService.logProjectAction(phase.getProjectId(),
                "DISBURSEMENT_RELEASED",
                "Funds released for phase ID: "
                        + savedDisbursement.getPhaseId()
                        + " | Amount: R"
                        + savedDisbursement.getAmount()
                        + " | Phase status changed to FUNDED"
        );

        return savedDisbursement;
    }

    /*
     * ============================================================
     * GET DISBURSEMENT
     * ============================================================
     */

    public Disbursement markProcessing(Long id) {
        Disbursement payment=locks.payment(id);
        WorkflowLocks.checkVersion(payment.getVersion());
        WorkflowLocks.requireActive(getProject(payment.getProjectId()));
        if (!"PENDING".equals(payment.getStatus()))
            throw new IllegalArgumentException("Only an approved payment awaiting release can enter processing.");
        payment.setStatus("PROCESSING");
        auditLogService.logProjectAction(payment.getProjectId(), "PAYMENT_PROCESSING",
                "Payment " + id + " is being processed. No funds recorded as released yet.");
        return disbursementRepository.save(payment);
    }

    public Disbursement uploadProof(Long id, org.springframework.web.multipart.MultipartFile file) {
        Disbursement payment=locks.payment(id);
        WorkflowLocks.checkVersion(payment.getVersion());
        if (!"RELEASED".equals(payment.getStatus()))
            throw new IllegalArgumentException("Record the payment before uploading proof of payment.");
        if (payment.getProofPath()!=null)
            throw new IllegalArgumentException("Proof already exists for this payment. It cannot be silently replaced.");
        String type=files.validate(file,false);
        String path=files.storePaymentProof(file);
        payment.setProofPath(path); payment.setProofName(files.displayName(file)); payment.setProofType(type);
        payment.setProofSize(file.getSize()); payment.setProofUploadedAt(LocalDateTime.now());
        payment.setProofUploadedBy(com.publicfund.backend.security.ProjectAccess.actor());
        auditLogService.logProjectAction(payment.getProjectId(), "PAYMENT_PROOF_UPLOADED",
                "Proof uploaded for payment " + id + " | Phase ID: " + payment.getPhaseId() + " | Reference: " + payment.getPaymentReference());
        return disbursementRepository.save(payment);
    }

    public org.springframework.core.io.Resource proof(Long id) {
        Disbursement payment=getDisbursementById(id);
        access.requireReader(getProject(payment.getProjectId()));
        return files.read(payment.getProofPath());
    }

    public Disbursement getDisbursementById(
            Long id) {

        return disbursementRepository
                .findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Disbursement not found"
                        ));
    }

    /*
     * ============================================================
     * GET DISBURSEMENTS BY PROJECT
     * ============================================================
     */
    public List<Disbursement> getDisbursementsByProject(
            Long projectId) {

        return disbursementRepository
                .findByProjectId(
                        projectId
                );
    }

    /*
     * ============================================================
     * GET DISBURSEMENTS BY PHASE
     * ============================================================
     */
    public List<Disbursement> getDisbursementsByPhase(
            Long phaseId) {

        return disbursementRepository
                .findByPhaseId(
                        phaseId
                );
    }

    /*
     * ============================================================
     * PROVIDER GETS OWN DISBURSEMENTS
     * ============================================================
     */
    public List<Disbursement> getDisbursementsByApplicant(
            Long applicantId) {

        List<Project> projects =
                projectRepository
                        .findByApplicantId(
                                applicantId
                        );

        List<Disbursement> disbursements =
                new ArrayList<>();

        for (Project project : projects) {

            disbursements.addAll(
                    disbursementRepository
                            .findByProjectId(
                                    project.getId()
                            )
            );
        }

        return disbursements;
    }

    /*
     * ============================================================
     * GET ALL DISBURSEMENTS
     * ============================================================
     */
    public List<Disbursement> getAllDisbursements() {

        return disbursementRepository
                .findAll();
    }

    /*
     * ============================================================
     * GET DISBURSEMENTS BY STATUS
     * ============================================================
     */
    public List<Disbursement> getDisbursementsByStatus(
            String status) {

        if (status == null ||
                status.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Disbursement status is required"
            );
        }

        return disbursementRepository
                .findByStatus(
                        status
                                .trim()
                                .toUpperCase()
                );
    }

    /*
     * ============================================================
     * HELPER - GET PHASE
     * ============================================================
     */
    private ProjectPhase getPhase(
            Long phaseId) {

        return phaseRepository
                .findById(phaseId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Project phase not found"
                        ));
    }

    /*
     * ============================================================
     * HELPER - GET PROJECT
     * ============================================================
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

    /*
     * ============================================================
     * HELPER - FIND PHASE BY ORDER
     * ============================================================
     */
    private ProjectPhase findPhaseByOrder(
            List<ProjectPhase> phases,
            Integer phaseOrder) {

        for (ProjectPhase phase : phases) {

            if (phase.getPhaseOrder()
                    .equals(phaseOrder)
                    && !"REJECTED".equals(
                            phase.getStatus()
                    )) {

                return phase;
            }
        }

        return null;
    }
}
