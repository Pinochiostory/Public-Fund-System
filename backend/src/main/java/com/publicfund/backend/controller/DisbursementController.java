package com.publicfund.backend.controller;

import com.publicfund.backend.entity.Disbursement;
import com.publicfund.backend.repository.UserRepository;
import com.publicfund.backend.service.DisbursementService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/disbursements")
@CrossOrigin(origins = "http://localhost:5173")
public class DisbursementController {

    private final DisbursementService disbursementService;
    private final UserRepository userRepository;

    public DisbursementController(
            DisbursementService disbursementService,
            UserRepository userRepository) {

        this.disbursementService = disbursementService;
        this.userRepository = userRepository;
    }

    @PostMapping("/phase/{phaseId}")
    public ResponseEntity<?> createDisbursement(
            @PathVariable Long phaseId) {

        try {

            Disbursement disbursement =
                    disbursementService
                            .createDisbursement(phaseId);

            return ResponseEntity.ok(disbursement);

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<?> releaseDisbursement(
            @PathVariable Long id, @RequestParam String paymentReference) {

        try {

            Disbursement disbursement =
                    disbursementService
                            .releaseDisbursement(id, paymentReference);

            return ResponseEntity.ok(disbursement);

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }


    @PostMapping("/{id}/processing")
    public ResponseEntity<?> processing(@PathVariable Long id) {
        return ResponseEntity.ok(disbursementService.markProcessing(id));
    }

    @PostMapping("/{id}/proof")
    public ResponseEntity<?> uploadProof(@PathVariable Long id, @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.ok(disbursementService.uploadProof(id,file));
    }

    @GetMapping("/{id}/proof")
    public ResponseEntity<?> proof(@PathVariable Long id) {
        var resource=disbursementService.proof(id);
        var payment=disbursementService.getDisbursementById(id);
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(payment.getProofType()))
                .header("X-Content-Type-Options","nosniff")
                .header("Content-Disposition",org.springframework.http.ContentDisposition.attachment()
                        .filename(payment.getProofName(),java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyDisbursements(
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
                    userRepository.findByEmail(email)
                            .map(user -> user.getId())
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "User account not found"
                                    ));

            return ResponseEntity.ok(
                    disbursementService
                            .getDisbursementsByApplicant(
                                    applicantId
                            )
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDisbursement(
            @PathVariable Long id) {

        try {

            return ResponseEntity.ok(
                    disbursementService
                            .getDisbursementById(id)
            );

        } catch (RuntimeException e) {

            return ResponseEntity.notFound()
                    .build();
        }
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<?> getProjectDisbursements(
            @PathVariable Long projectId) {

        return ResponseEntity.ok(
                disbursementService
                        .getDisbursementsByProject(
                                projectId
                        )
        );
    }

    @GetMapping("/phase/{phaseId}")
    public ResponseEntity<?> getPhaseDisbursements(
            @PathVariable Long phaseId) {

        return ResponseEntity.ok(
                disbursementService
                        .getDisbursementsByPhase(
                                phaseId
                        )
        );
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<?> getDisbursementsByStatus(
            @PathVariable String status) {

        return ResponseEntity.ok(
                disbursementService
                        .getDisbursementsByStatus(
                                status
                        )
        );
    }

    @GetMapping
    public ResponseEntity<?> getAllDisbursements() {

        return ResponseEntity.ok(
                disbursementService
                        .getAllDisbursements()
        );
    }
}
