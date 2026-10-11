package com.publicfund.backend.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.publicfund.backend.entity.FundingApplication;
import com.publicfund.backend.repository.UserRepository;
import com.publicfund.backend.service.FundingApplicationService;

@RestController
@RequestMapping("/api/applications")
@CrossOrigin(origins = "http://localhost:5173")
public class FundingApplicationController {

    private final FundingApplicationService applicationService;
    private final UserRepository userRepository;

    public FundingApplicationController(
            FundingApplicationService applicationService,
            UserRepository userRepository) {

        this.applicationService = applicationService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> createApplication(
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam BigDecimal requestedBudget,
            @RequestParam String timeline,
            @RequestParam(required = false) java.time.LocalDate expectedCompletionDate,
            Authentication authentication) {

        try {

            Long applicantId = getApplicantId(authentication);

            FundingApplication application =
                    applicationService.createApplication(
                            applicantId,
                            title,
                            description,
                            requestedBudget,
                            timeline, expectedCompletionDate
                    );

            return ResponseEntity.ok(application);

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyApplications(
            Authentication authentication) {

        try {

            Long applicantId = getApplicantId(authentication);

            List<FundingApplication> applications =
                    applicationService
                            .getApplicationsByApplicant(
                                    applicantId
                            );

            return ResponseEntity.ok(applications);

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllApplications() {

        try {

            return ResponseEntity.ok(
                    applicationService
                            .getAllApplications()
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getApplication(
            @PathVariable Long id) {

        try {

            return ResponseEntity.ok(
                    applicationService
                            .getApplicationById(id)
            );

        } catch (RuntimeException e) {

            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<?> getApplicationsByStatus(
            @PathVariable String status) {

        try {

            return ResponseEntity.ok(
                    applicationService
                            .getApplicationsByStatus(status)
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveApplication(
            @PathVariable Long id) {

        try {

            FundingApplication application =
                    applicationService
                            .approveApplication(id);

            return ResponseEntity.ok(application);

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectApplication(
            @PathVariable Long id, @RequestParam String rejectionReason) {

        try {

            FundingApplication application =
                    applicationService
                            .rejectApplication(id, rejectionReason);

            return ResponseEntity.ok(application);

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    private Long getApplicantId(
            Authentication authentication) {

        if (authentication == null) {

            throw new RuntimeException(
                    "Authentication is required"
            );
        }

        String email =
                authentication.getName();

        return userRepository
                .findByEmail(email)
                .map(user -> user.getId())
                .orElseThrow(() ->
                        new RuntimeException(
                                "User account not found"
                        ));
    }
}
