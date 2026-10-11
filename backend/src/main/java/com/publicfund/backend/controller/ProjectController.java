package com.publicfund.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.publicfund.backend.entity.Project;
import com.publicfund.backend.repository.UserRepository;
import com.publicfund.backend.service.ProjectService;

@RestController
@RequestMapping("/api/projects")
@CrossOrigin(origins = "http://localhost:5173")
public class ProjectController {

    private final ProjectService projectService;
    private final UserRepository userRepository;

    public ProjectController(
            ProjectService projectService,
            UserRepository userRepository) {

        this.projectService = projectService;
        this.userRepository = userRepository;
    }

    /*
     * ADMIN CREATES PROJECT FROM APPROVED APPLICATION
     */
    @PostMapping("/from-application/{applicationId}")
    public ResponseEntity<?> createProject(
            @PathVariable Long applicationId) {

        try {

            Project project =
                    projectService.createProject(
                            applicationId
                    );

            return ResponseEntity.ok(project);

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * SERVICE PROVIDER REQUESTS PROJECT COMPLETION
     *
     * All project phases must already be COMPLETED.
     */
    @PostMapping("/{id}/request-completion")
    public ResponseEntity<?> requestProjectCompletion(
            @PathVariable Long id,
            Authentication authentication) {

        try {

            Long applicantId =
                    getApplicantId(
                            authentication
                    );

            Project project =
                    projectService
                            .requestProjectCompletion(
                                    id,
                                    applicantId
                            );

            return ResponseEntity.ok(project);

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * ADMIN FORMALLY CONCLUDES PROJECT
     *
     * Project must already be PENDING_COMPLETION.
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<?> completeProject(
            @PathVariable Long id) {

        try {

            Project project =
                    projectService.completeProject(
                            id
                    );

            return ResponseEntity.ok(project);

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * SERVICE PROVIDER GETS OWN PROJECTS
     */

    @PostMapping("/{id}/closeout-review")
    public ResponseEntity<?> reviewCloseout(@PathVariable Long id,
            @RequestParam String reference, @RequestParam String note) {
        return ResponseEntity.ok(projectService.reviewCloseout(id, reference, note));
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<?> summary(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.summary(id));
    }

    @GetMapping("/{id}/activity")
    public ResponseEntity<?> activity(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.activity(id));
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyProjects(
            Authentication authentication) {

        try {

            Long applicantId =
                    getApplicantId(
                            authentication
                    );

            List<Project> projects =
                    projectService
                            .getProjectsByApplicant(
                                    applicantId
                            );

            return ResponseEntity.ok(projects);

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * GET SINGLE PROJECT
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getProject(
            @PathVariable Long id) {

        try {

            return ResponseEntity.ok(
                    projectService
                            .getProjectById(
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
     * GET PROJECT USING APPLICATION ID
     */
    @GetMapping("/application/{applicationId}")
    public ResponseEntity<?> getProjectByApplication(
            @PathVariable Long applicationId) {

        try {

            return ResponseEntity.ok(
                    projectService
                            .getProjectByApplicationId(
                                    applicationId
                            )
            );

        } catch (RuntimeException e) {

            return ResponseEntity
                    .notFound()
                    .build();
        }
    }

    /*
     * ADMIN GETS PROJECTS FOR SPECIFIC APPLICANT
     */
    @GetMapping("/applicant/{applicantId}")
    public ResponseEntity<?> getProjectsByApplicant(
            @PathVariable Long applicantId) {

        try {

            return ResponseEntity.ok(
                    projectService
                            .getProjectsByApplicant(
                                    applicantId
                            )
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * ADMIN GETS ALL PROJECTS
     */
    @GetMapping
    public ResponseEntity<?> getAllProjects() {

        try {

            return ResponseEntity.ok(
                    projectService
                            .getAllProjects()
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * ADMIN GETS PROJECTS BY STATUS
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<?> getProjectsByStatus(
            @PathVariable String status) {

        try {

            return ResponseEntity.ok(
                    projectService
                            .getProjectsByStatus(
                                    status
                            )
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    /*
     * GET LOGGED-IN SERVICE PROVIDER ID
     */
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
                .map(user ->
                        user.getId()
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "User account not found"
                        ));
    }
}
