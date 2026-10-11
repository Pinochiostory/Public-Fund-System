package com.publicfund.backend.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

import com.publicfund.backend.entity.FundingApplication;
import com.publicfund.backend.repository.FundingApplicationRepository;

@org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
@Service
public class FundingApplicationService {
    private final com.publicfund.backend.repository.UserRepository users;

    private final FundingApplicationRepository applicationRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public FundingApplicationService(com.publicfund.backend.repository.UserRepository users, 
            FundingApplicationRepository applicationRepository,
            AuditLogService auditLogService,
            NotificationService notificationService) {
        this.users = users;

        this.applicationRepository = applicationRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    public FundingApplication createApplication(
            Long applicantId,
            String title,
            String description,
            BigDecimal requestedBudget,
            String timeline, java.time.LocalDate expectedCompletionDate) {

        if (applicantId == null) {
            throw new IllegalArgumentException(
                    "Applicant ID is required"
            );
        }

        if (title == null ||
                title.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Application title is required"
            );
        }

        if (description == null ||
                description.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Application description is required"
            );
        }

        if (requestedBudget == null ||
                requestedBudget.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Requested budget must be greater than zero"
            );
        }

        if (timeline == null ||
                timeline.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Project timeline is required"
            );
        }

        if (expectedCompletionDate != null && expectedCompletionDate.isBefore(java.time.LocalDate.now()))
            throw new IllegalArgumentException("Expected completion date cannot be in the past.");
        if (title.trim().length()>255 || timeline.trim().length()>255 || requestedBudget.scale()>2 || requestedBudget.precision()>15)
            throw new IllegalArgumentException("Use a title and timeline under 256 characters and a valid budget with at most two decimal places.");
        users.findByIdForUpdate(applicantId).orElseThrow(() -> new IllegalArgumentException("User account not found."));
        if (applicationRepository.findByApplicantId(applicantId).stream().anyMatch(a -> a.getTitle().equalsIgnoreCase(title.trim()) && a.getDescription().equals(description.trim()) && a.getRequestedBudget().compareTo(requestedBudget) == 0)) throw new IllegalArgumentException("This application has already been submitted. View your existing application before submitting again.");
        FundingApplication application =
                new FundingApplication(
                        applicantId,
                        title,
                        description,
                        requestedBudget,
                        timeline
                );

        application.setExpectedCompletionDate(expectedCompletionDate);
        FundingApplication savedApplication =
                applicationRepository.save(
                        application
                );

        auditLogService.logApplicationAction(savedApplication.getId(),
                "APPLICATION_SUBMITTED",
                "Funding application submitted: "
                        + savedApplication.getTitle()
                        + " | Application ID: "
                        + savedApplication.getId()
        );

        return savedApplication;
    }

    public List<FundingApplication>
            getApplicationsByApplicant(
                    Long applicantId) {

        return applicationRepository
                .findByApplicantId(
                        applicantId
                );
    }

    public List<FundingApplication>
            getApplicationsByStatus(
                    String status) {

        return applicationRepository
                .findByStatus(status);
    }

    public List<FundingApplication>
            getAllApplications() {

        return applicationRepository.findAll();
    }

    public FundingApplication getApplicationById(
            Long id) {

        return applicationRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Funding application not found"
                        ));
    }

    public FundingApplication approveApplication(
            Long id) {

        FundingApplication application =
                applicationRepository.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Application not found."));
        WorkflowLocks.checkVersion(application.getVersion());

        if (!application.getStatus()
                .equals("PENDING")) {

            throw new IllegalArgumentException(
                    "Only pending applications can be approved"
            );
        }

        application.setStatus("APPROVED");

        FundingApplication savedApplication =
                applicationRepository.save(
                        application
                );

        notificationService.createNotification(
                application.getApplicantId(),
                "Application Approved",
                "Your funding application \""
                        + application.getTitle()
                        + "\" has been approved."
        );

        auditLogService.logApplicationAction(id,
                "APPLICATION_APPROVED",
                "Funding application approved. Application ID: "
                        + id + " | Approved budget: R" + application.getRequestedBudget()
        );

        return savedApplication;
    }

    public FundingApplication rejectApplication(Long id, String rejectionReason) {

        FundingApplication application =
                applicationRepository.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Application not found."));
        WorkflowLocks.checkVersion(application.getVersion());

        if (!application.getStatus()
                .equals("PENDING")) {

            throw new IllegalArgumentException(
                    "Only pending applications can be rejected"
            );
        }

        if (rejectionReason == null || rejectionReason.trim().isEmpty()) throw new IllegalArgumentException("A reason for rejection is required.");
        if (rejectionReason.length() > 4000) throw new IllegalArgumentException("Keep the rejection reason under 4,000 characters.");
        application.setRejectionReason(rejectionReason.trim());
        application.setRejectedAt(java.time.LocalDateTime.now());
        application.setRejectedBy(com.publicfund.backend.security.ProjectAccess.actor());
        application.setStatus("REJECTED");

        FundingApplication savedApplication =
                applicationRepository.save(
                        application
                );

        notificationService.createNotification(
                application.getApplicantId(),
                "Application Rejected",
                "Your funding application \""
                        + application.getTitle()
                        + "\" was not approved. Reason: " + application.getRejectionReason() + ""
        );

        auditLogService.logApplicationAction(id,
                "APPLICATION_REJECTED",
                "Funding application rejected. Application ID: "
                        + id + " | Reason: " + application.getRejectionReason()
        );

        return savedApplication;
    }
}
