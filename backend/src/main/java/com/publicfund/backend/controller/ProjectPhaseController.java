package com.publicfund.backend.controller;

import com.publicfund.backend.entity.ProjectPhase;
import com.publicfund.backend.repository.UserRepository;
import com.publicfund.backend.service.ProjectPhaseService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/project-phases")
@CrossOrigin(origins = "http://localhost:5173")
public class ProjectPhaseController {

    private final ProjectPhaseService phaseService;
    private final UserRepository userRepository;

    public ProjectPhaseController(
            ProjectPhaseService phaseService,
            UserRepository userRepository) {

        this.phaseService = phaseService;
        this.userRepository = userRepository;
    }

    /*
     * SERVICE PROVIDER SUBMITS PHASE PROPOSAL
     */
    @PostMapping("/project/{projectId}")
    public ResponseEntity<?> createPhase(
            @PathVariable Long projectId,
            @RequestParam String phaseName,
            @RequestParam String description,
            @RequestParam BigDecimal allocatedBudget,
            @RequestParam Integer phaseOrder, @RequestParam(required = false) java.time.LocalDate deadline) {

        try {

            ProjectPhase phase =
                    phaseService.createPhase(
                            projectId,
                            phaseName,
                            description,
                            allocatedBudget,
                            phaseOrder, deadline
                    );

            return ResponseEntity.ok(phase);

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * ADMIN APPROVES PHASE PROPOSAL
     */
    @PostMapping("/{phaseId}/approve")
    public ResponseEntity<?> approvePhase(
            @PathVariable Long phaseId) {

        try {

            ProjectPhase phase =
                    phaseService.approvePhase(
                            phaseId
                    );

            return ResponseEntity.ok(phase);

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * ADMIN REJECTS PHASE PROPOSAL
     */
    @PostMapping("/{phaseId}/reject")
    public ResponseEntity<?> rejectPhase(
            @PathVariable Long phaseId,
            @RequestParam String rejectionReason) {

        try {

            ProjectPhase phase =
                    phaseService.rejectPhase(
                            phaseId,
                            rejectionReason
                    );

            return ResponseEntity.ok(phase);

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * SERVICE PROVIDER STARTS FUNDED PHASE
     */
    @PostMapping("/{phaseId}/start")
    public ResponseEntity<?> startPhase(
            @PathVariable Long phaseId) {

        try {

            ProjectPhase phase =
                    phaseService.startPhase(
                            phaseId
                    );

            return ResponseEntity.ok(phase);

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * ADMIN FORMALLY CONCLUDES PHASE
     *
     * Phase must already be EVIDENCE_APPROVED.
     */
    @PostMapping("/{phaseId}/conclude")
    public ResponseEntity<?> concludePhase(
            @PathVariable Long phaseId) {

        try {

            ProjectPhase phase =
                    phaseService.concludePhase(
                            phaseId
                    );

            return ResponseEntity.ok(phase);

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * SERVICE PROVIDER GETS OWN PHASES
     */
    @GetMapping("/my")
    public ResponseEntity<?> getMyPhases(
            Authentication authentication) {

        try {

            if (authentication == null) {

                throw new RuntimeException(
                        "Authentication is required"
                );
            }

            String email =
                    authentication.getName();

            Long applicantId =
                    userRepository
                            .findByEmail(email)
                            .map(user ->
                                    user.getId()
                            )
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "User account not found"
                                    ));

            List<ProjectPhase> phases =
                    phaseService
                            .getPhasesByApplicant(
                                    applicantId
                            );

            return ResponseEntity.ok(phases);

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * GET SINGLE PHASE
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getPhase(
            @PathVariable Long id) {

        try {

            return ResponseEntity.ok(
                    phaseService.getPhaseById(id)
            );

        } catch (RuntimeException e) {

            return ResponseEntity
                    .notFound()
                    .build();
        }
    }

    /*
     * GET PHASES FOR PROJECT
     */
    @GetMapping("/project/{projectId}")
    public ResponseEntity<?> getProjectPhases(
            @PathVariable Long projectId) {

        try {

            List<ProjectPhase> phases =
                    phaseService
                            .getPhasesByProject(
                                    projectId
                            );

            return ResponseEntity.ok(phases);

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * GET PHASES BY STATUS
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<?> getPhasesByStatus(
            @PathVariable String status) {

        try {

            return ResponseEntity.ok(
                    phaseService
                            .getPhasesByStatus(
                                    status
                            )
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * ADMIN / OFFICER GET ALL PHASES
     */
    @GetMapping
    public ResponseEntity<?> getAllPhases() {

        try {

            return ResponseEntity.ok(
                    phaseService
                            .getAllPhases()
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }
}
