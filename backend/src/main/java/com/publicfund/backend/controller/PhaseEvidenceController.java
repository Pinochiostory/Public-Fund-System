package com.publicfund.backend.controller;

import com.publicfund.backend.entity.PhaseEvidence;
import com.publicfund.backend.repository.UserRepository;
import com.publicfund.backend.service.PhaseEvidenceService;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/evidence")
@CrossOrigin(origins = "http://localhost:5173")
public class PhaseEvidenceController {

    private final PhaseEvidenceService evidenceService;
    private final UserRepository userRepository;

    public PhaseEvidenceController(
            PhaseEvidenceService evidenceService,
            UserRepository userRepository) {

        this.evidenceService =
                evidenceService;

        this.userRepository =
                userRepository;
    }

    /*
     * ============================================================
     * SERVICE PROVIDER UPLOADS EVIDENCE
     * ============================================================
     */
    @PostMapping("/phase/{phaseId}/batch")
    public ResponseEntity<?> uploadEvidenceBatch(@PathVariable Long phaseId,
            @RequestParam("files") List<MultipartFile> files, Authentication authentication) {
        return ResponseEntity.ok(evidenceService.uploadEvidenceBatch(phaseId, getCurrentUserId(authentication), files));
    }

    @PostMapping("/phase/{phaseId}")
    public ResponseEntity<?> uploadEvidence(
            @PathVariable Long phaseId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        try {

            Long uploadedBy =
                    getCurrentUserId(
                            authentication
                    );

            PhaseEvidence evidence =
                    evidenceService.uploadEvidence(
                            phaseId,
                            uploadedBy,
                            file
                    );

            return ResponseEntity.ok(
                    evidence
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * ============================================================
     * SERVICE PROVIDER GETS OWN EVIDENCE
     * ============================================================
     */
    @GetMapping("/my")
    public ResponseEntity<?> getMyEvidence(
            Authentication authentication) {

        try {

            Long userId =
                    getCurrentUserId(
                            authentication
                    );

            List<PhaseEvidence> myEvidence =
                    evidenceService
                            .getAllEvidence()
                            .stream()
                            .filter(
                                    evidence ->
                                            evidence
                                                    .getUploadedBy()
                                                    .equals(
                                                            userId
                                                    )
                            )
                            .toList();

            return ResponseEntity.ok(
                    myEvidence
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * ============================================================
     * VERIFICATION OFFICER APPROVES EVIDENCE
     * ============================================================
     */

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<?> withdraw(@PathVariable Long id, @RequestParam String reason, Authentication authentication) {
        return ResponseEntity.ok(evidenceService.withdraw(id,getCurrentUserId(authentication),reason));
    }

    @PostMapping("/{evidenceId}/approve")
    public ResponseEntity<?> approveEvidence(
            @PathVariable Long evidenceId,
            @RequestParam(required = false)
            String verificationComment) {

        try {

            PhaseEvidence evidence =
                    evidenceService
                            .approveEvidence(
                                    evidenceId,
                                    verificationComment
                            );

            return ResponseEntity.ok(
                    evidence
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * ============================================================
     * VERIFICATION OFFICER REJECTS EVIDENCE
     * ============================================================
     */
    @PostMapping("/{evidenceId}/reject")
    public ResponseEntity<?> rejectEvidence(
            @PathVariable Long evidenceId,
            @RequestParam String verificationComment) {

        try {

            PhaseEvidence evidence =
                    evidenceService
                            .rejectEvidence(
                                    evidenceId,
                                    verificationComment
                            );

            return ResponseEntity.ok(
                    evidence
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * ============================================================
     * GET ALL EVIDENCE
     * ============================================================
     */
    @GetMapping
    public ResponseEntity<?> getAllEvidence() {

        return ResponseEntity.ok(
                evidenceService
                        .getAllEvidence()
        );
    }

    /*
     * ============================================================
     * OPEN EVIDENCE FILE
     * ============================================================
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<?> getEvidenceFile(
            @PathVariable Long id, Authentication authentication) {

        try {

            PhaseEvidence evidence =
                    evidenceService
                            .getEvidenceById(
                                    id
                            );

            boolean officer = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(authority -> java.util.Set.of("ROLE_VERIFICATION_OFFICER", "ROLE_ADMINISTRATOR").contains(authority.getAuthority()));
            if (!officer && !java.util.Objects.equals(evidence.getUploadedBy(), getCurrentUserId(authentication))) {
                return ResponseEntity.notFound().build();
            }
            Resource resource =
                    evidenceService
                            .getEvidenceFile(
                                    id
                            );

            MediaType mediaType;

            try {

                mediaType =
                        MediaType.parseMediaType(
                                evidence.getFileType()
                        );

            } catch (Exception e) {

                mediaType =
                        MediaType.APPLICATION_OCTET_STREAM;
            }

            return ResponseEntity
                    .ok()
                    .contentType(
                            mediaType
                    )
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            org.springframework.http.ContentDisposition.attachment().filename(evidence.getFileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString()
                    )
                    .body(
                            resource
                    );

        } catch (RuntimeException e) {

            return ResponseEntity
                    .notFound()
                    .build();
        }
    }

    /*
     * ============================================================
     * GET SINGLE EVIDENCE
     * ============================================================
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getEvidence(
            @PathVariable Long id) {

        try {

            return ResponseEntity.ok(
                    evidenceService
                            .getEvidenceById(
                                    id
                            )
            );

        } catch (RuntimeException e) {

            return ResponseEntity
                    .notFound()
                    .build();
        }
    }

    /*
     * ============================================================
     * GET EVIDENCE FOR PHASE
     * ============================================================
     */
    @GetMapping("/phase/{phaseId}")
    public ResponseEntity<?> getEvidenceByPhase(
            @PathVariable Long phaseId) {

        return ResponseEntity.ok(
                evidenceService
                        .getEvidenceByPhase(
                                phaseId
                        )
        );
    }

    /*
     * ============================================================
     * GET EVIDENCE BY STATUS
     * ============================================================
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<?> getEvidenceByStatus(
            @PathVariable String status) {

        try {

            return ResponseEntity.ok(
                    evidenceService
                            .getEvidenceByStatus(
                                    status
                            )
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * ============================================================
     * GET PENDING EVIDENCE FOR PHASE
     * ============================================================
     */
    @GetMapping("/phase/{phaseId}/pending")
    public ResponseEntity<?> getPendingEvidence(
            @PathVariable Long phaseId) {

        return ResponseEntity.ok(
                evidenceService
                        .getPendingEvidenceForPhase(
                                phaseId
                        )
        );
    }

    /*
     * ============================================================
     * GET LOGGED-IN USER ID
     * ============================================================
     */
    private Long getCurrentUserId(
            Authentication authentication) {

        if (authentication == null) {

            throw new RuntimeException(
                    "Authentication is required"
            );
        }

        String email =
                authentication.getName();

        return userRepository
                .findByEmail(
                        email
                )
                .map(
                        user ->
                                user.getId()
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "User account not found"
                        )
                );
    }
}
