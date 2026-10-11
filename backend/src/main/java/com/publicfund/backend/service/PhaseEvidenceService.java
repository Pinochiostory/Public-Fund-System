package com.publicfund.backend.service;

import com.publicfund.backend.entity.PhaseEvidence;
import com.publicfund.backend.entity.Project;
import com.publicfund.backend.entity.ProjectPhase;

import com.publicfund.backend.repository.PhaseEvidenceRepository;
import com.publicfund.backend.repository.ProjectPhaseRepository;
import com.publicfund.backend.repository.ProjectRepository;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
@Service
public class PhaseEvidenceService {
    private final WorkflowLocks locks;
    private final com.publicfund.backend.repository.UserRepository users;

    private final PhaseEvidenceRepository evidenceRepository;
    private final ProjectPhaseRepository phaseRepository;
    private final ProjectRepository projectRepository;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public PhaseEvidenceService(com.publicfund.backend.repository.UserRepository users, WorkflowLocks locks, 
            PhaseEvidenceRepository evidenceRepository,
            ProjectPhaseRepository phaseRepository,
            ProjectRepository projectRepository,
            FileStorageService fileStorageService,
            AuditLogService auditLogService,
            NotificationService notificationService) {
        this.locks = locks; this.users=users;

        this.evidenceRepository =
                evidenceRepository;

        this.phaseRepository =
                phaseRepository;

        this.projectRepository =
                projectRepository;

        this.fileStorageService =
                fileStorageService;

        this.auditLogService =
                auditLogService;

        this.notificationService =
                notificationService;
    }

    /*
     * ============================================================
     * SERVICE PROVIDER UPLOADS EVIDENCE
     * ============================================================
     */
    public PhaseEvidence uploadEvidence(Long phaseId, Long uploadedBy, MultipartFile file) {
        return uploadEvidenceBatch(phaseId, uploadedBy, java.util.Collections.singletonList(file)).getFirst();
    }

    public List<PhaseEvidence> uploadEvidenceBatch(Long phaseId, Long uploadedBy, List<MultipartFile> files) {
        ProjectPhase phase = locks.phase(phaseId);
        Project project = getProject(phase.getProjectId());
        WorkflowLocks.requireActive(project);
        if (!java.util.Objects.equals(project.getApplicantId(), uploadedBy))
            throw new org.springframework.security.access.AccessDeniedException("You can only upload evidence for your own project.");
        if (!"IN_PROGRESS".equals(phase.getStatus()))
            throw new IllegalArgumentException("Evidence can only be uploaded for a phase that is in progress.");
        if (!evidenceRepository.findByPhaseIdAndStatus(phaseId, "PENDING").isEmpty())
            throw new IllegalArgumentException("Your current evidence submission is awaiting review. Wait for every file to be reviewed before submitting a corrected set.");
        if (files == null || files.isEmpty() || files.size() > 10)
            throw new IllegalArgumentException("Choose between 1 and 10 evidence files per submission.");
        long total = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) throw new IllegalArgumentException("Every evidence file must contain content.");
            if (file.getSize() >= 25L * 1024 * 1024) throw new IllegalArgumentException("Each evidence file must be less than 25 MB.");
            total += file.getSize();
        }
        if (total >= 100L * 1024 * 1024) throw new IllegalArgumentException("The complete evidence submission must be less than 100 MB.");
        // Validate the entire set before storage. Rollback cleanup also covers disk/DB failure.
        java.util.List<String> types = new java.util.ArrayList<>();
        java.util.Set<String> hashes = new java.util.HashSet<>();
        for (MultipartFile file : files) {
            types.add(fileStorageService.validate(file, true));
            try (var input = file.getInputStream()) {
                var digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192]; int length;
                while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
                if (!hashes.add(java.util.HexFormat.of().formatHex(digest.digest())))
                    throw new IllegalArgumentException("The same file appears more than once. Remove duplicate files before uploading.");
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException("An evidence file could not be read. No files were submitted.");
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException("File validation is unavailable.");
            }
        }
        String submission = java.util.UUID.randomUUID().toString();
        java.util.List<PhaseEvidence> saved = new java.util.ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String path = fileStorageService.storeEvidenceFile(file);
            PhaseEvidence item = new PhaseEvidence(phaseId, uploadedBy,
                    fileStorageService.displayName(file), types.get(i), path);
            item.setFileSize(file.getSize());
            item.setSubmissionId(submission);
            saved.add(evidenceRepository.save(item));
            auditLogService.logProjectAction(project.getId(), "EVIDENCE_UPLOADED",
                    "Evidence uploaded for phase ID: " + phaseId + " | Evidence ID: " + item.getId()
                    + " | Submission: " + submission + " | File: " + item.getFileName());
        }
        return saved;
    }

    /*
     * ============================================================
     * VERIFICATION OFFICER APPROVES EVIDENCE
     * ============================================================
     *
     * IMPORTANT:
     *
     * Evidence approval does NOT complete the phase.
     *
     * The phase becomes EVIDENCE_APPROVED and must then
     * be formally concluded by an Administrator.
     * ============================================================
     */
    public PhaseEvidence approveEvidence(
            Long evidenceId,
            String verificationComment) {

        PhaseEvidence evidence = locks.evidence(evidenceId);
        WorkflowLocks.checkVersion(evidence.getVersion());

        if (!"PENDING".equals(
                evidence.getStatus())) {

            throw new IllegalArgumentException(
                    "Only pending evidence can be approved"
            );
        }

        ProjectPhase phase =
                phaseRepository
                        .findById(
                                evidence.getPhaseId()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Project phase not found"
                                ));

        if (!"IN_PROGRESS".equals(
                phase.getStatus())) {

            throw new IllegalArgumentException(
                    "Evidence can only be approved for a phase that is in progress"
            );
        }

        WorkflowLocks.requireActive(getProject(phase.getProjectId()));
        fileStorageService.read(evidence.getFilePath());
        String comment =
                cleanComment(
                        verificationComment
                );

        evidence.setStatus(
                "APPROVED"
        );

        evidence.setVerificationComment(
                comment
        );

        evidence.setVerifiedAt(
                LocalDateTime.now()
        );
        evidence.setReviewedBy(com.publicfund.backend.security.ProjectAccess.actor());

        PhaseEvidence savedEvidence =
                evidenceRepository
                        .save(
                                evidence
                        );

        /*
         * Verification Officer does NOT complete the phase.
         */
        boolean allApproved = EvidenceSubmissions.fullyApproved(evidenceRepository.findByPhaseId(phase.getId()));
        if (allApproved) phase.setStatus("EVIDENCE_APPROVED");

        phaseRepository.save(
                phase
        );

        Project project =
                getProject(
                        phase.getProjectId()
                );

        notificationService.createNotification(
                project.getApplicantId(),
                "Evidence Approved",
                "Evidence for phase \""
                        + phase.getPhaseName()
                        + "\" has been approved by a reviewer. "
                        + (allApproved ? "Every file in this submission is approved. The phase is awaiting formal conclusion by the Administrator." : "The phase remains in progress until every file in the latest submission is approved.")
        );

        auditLogService.logProjectAction(phase.getProjectId(),
                "EVIDENCE_APPROVED",
                "Evidence approved. Evidence ID: "
                        + evidenceId
                        + " | Phase ID: "
                        + phase.getId()
                        + " | Phase status: " + phase.getStatus()
        );

        return savedEvidence;
    }

    /*
     * ============================================================
     * VERIFICATION OFFICER REJECTS EVIDENCE
     * ============================================================
     *
     * Phase remains IN_PROGRESS.
     *
     * Provider may submit corrected evidence.
     * ============================================================
     */
    public PhaseEvidence rejectEvidence(
            Long evidenceId,
            String verificationComment) {

        PhaseEvidence evidence = locks.evidence(evidenceId);
        WorkflowLocks.checkVersion(evidence.getVersion());

        if (!"PENDING".equals(
                evidence.getStatus())) {

            throw new IllegalArgumentException(
                    "Only pending evidence can be rejected"
            );
        }

        String comment =
                cleanComment(
                        verificationComment
                );

        if (comment == null) {

            throw new IllegalArgumentException(
                    "A verification comment is required when rejecting evidence"
            );
        }

        ProjectPhase phase =
                phaseRepository
                        .findById(
                                evidence.getPhaseId()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Project phase not found"
                                ));

        if (!"IN_PROGRESS".equals(
                phase.getStatus())) {

            throw new IllegalArgumentException(
                    "Evidence can only be rejected while the phase is in progress"
            );
        }

        evidence.setStatus(
                "REJECTED"
        );

        evidence.setVerificationComment(
                comment
        );

        evidence.setVerifiedAt(
                LocalDateTime.now()
        );
        evidence.setReviewedBy(com.publicfund.backend.security.ProjectAccess.actor());

        PhaseEvidence savedEvidence =
                evidenceRepository
                        .save(
                                evidence
                        );

        /*
         * DO NOT change phase status.
         *
         * It remains IN_PROGRESS so corrected evidence
         * can be submitted.
         */

        Project project =
                getProject(
                        phase.getProjectId()
                );

        notificationService.createNotification(
                project.getApplicantId(),
                "Evidence Rejected",
                "Evidence for phase \""
                        + phase.getPhaseName()
                        + "\" was rejected. Reason: "
                        + comment
                        + ". Once all files have been reviewed, submit a complete corrected set of evidence. Previous files remain in history."
        );

        auditLogService.logProjectAction(phase.getProjectId(),
                "EVIDENCE_REJECTED",
                "Evidence rejected. Evidence ID: "
                        + evidenceId
                        + " | Phase ID: "
                        + phase.getId() + " | Reason: " + comment
        );

        return savedEvidence;
    }

    /*
     * ============================================================
     * GET EVIDENCE FILE
     * ============================================================
     */

    public PhaseEvidence withdraw(Long id, Long userId, String reason) {
        PhaseEvidence item=locks.evidence(id);
        WorkflowLocks.checkVersion(item.getVersion());
        ProjectPhase phase=getPhaseForEvidence(item);
        Project project=getProject(phase.getProjectId());
        if (!java.util.Objects.equals(project.getApplicantId(),userId))
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to perform this action.");
        WorkflowLocks.requireActive(project);
        if (!"PENDING".equals(item.getStatus()) || !"IN_PROGRESS".equals(phase.getStatus()))
            throw new IllegalArgumentException("Only evidence awaiting review can be withdrawn for replacement.");
        if (reason==null || reason.isBlank() || reason.length()>4000)
            throw new IllegalArgumentException("Provide a replacement reason between 1 and 4,000 characters.");
        item.setStatus("WITHDRAWN");
        item.setVerificationComment("Withdrawn by Service Provider: " + reason.trim());
        auditLogService.logProjectAction(project.getId(),"EVIDENCE_WITHDRAWN",
                "Evidence " + id + " withdrawn for replacement. Phase ID: " + phase.getId() + " | Reason: " + reason.trim());
        return evidenceRepository.save(item);
    }

    private ProjectPhase getPhaseForEvidence(PhaseEvidence evidence) {
        return phaseRepository.findById(evidence.getPhaseId()).orElseThrow(() -> new IllegalArgumentException("Phase not found."));
    }

    public Resource getEvidenceFile(Long evidenceId) {
        return fileStorageService.read(getEvidenceById(evidenceId).getFilePath());
    }

    /*
     * ============================================================
     * GET EVIDENCE BY ID
     * ============================================================
     */
    public PhaseEvidence getEvidenceById(
            Long id) {

        return evidenceRepository
                .findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Evidence not found"
                        ));
    }

    /*
     * ============================================================
     * GET EVIDENCE FOR PHASE
     * ============================================================
     */
    public List<PhaseEvidence> getEvidenceByPhase(
            Long phaseId) {

        return evidenceRepository
                .findByPhaseId(
                        phaseId
                );
    }

    /*
     * ============================================================
     * GET EVIDENCE BY STATUS
     * ============================================================
     */
    public List<PhaseEvidence> getEvidenceByStatus(
            String status) {

        if (status == null ||
                status.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Evidence status is required"
            );
        }

        return evidenceRepository
                .findByStatus(
                        status
                                .trim()
                                .toUpperCase()
                );
    }

    /*
     * ============================================================
     * GET PENDING EVIDENCE FOR PHASE
     * ============================================================
     */
    public List<PhaseEvidence> getPendingEvidenceForPhase(
            Long phaseId) {

        return evidenceRepository
                .findByPhaseIdAndStatus(
                        phaseId,
                        "PENDING"
                );
    }

    /*
     * ============================================================
     * GET ALL EVIDENCE
     * ============================================================
     */
    public List<PhaseEvidence> getAllEvidence() {
        List<PhaseEvidence> items=evidenceRepository.findAll();
        java.util.Map<Long,String> names=new java.util.HashMap<>();
        users.findAllById(items.stream().map(PhaseEvidence::getUploadedBy).distinct().toList()).forEach(u->names.put(u.getId(),u.getFullName()));
        items.forEach(item->item.setProviderName(names.get(item.getUploadedBy())));
        return items;
    }

    /*
     * ============================================================
     * GET PROJECT
     * ============================================================
     */
    private Project getProject(
            Long projectId) {

        return projectRepository
                .findById(
                        projectId
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Project not found"
                        ));
    }

    /*
     * ============================================================
     * CLEAN VERIFICATION COMMENT
     * ============================================================
     */
    private String cleanComment(
            String verificationComment) {

        if (verificationComment == null) {

            return null;
        }

        if (verificationComment.length()>4000) throw new IllegalArgumentException("Keep the review comment under 4,001 characters.");
        String cleaned =
                verificationComment
                        .trim();

        if (cleaned.isEmpty()) {

            return null;
        }

        return cleaned;
    }
}
