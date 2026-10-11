package com.publicfund.backend.service;

import com.publicfund.backend.entity.*;
import com.publicfund.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** All mutations acquire locks in project -> phase -> payment/evidence order. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class WorkflowLocks {
    private final ProjectRepository projects;
    private final ProjectPhaseRepository phases;
    private final DisbursementRepository payments;
    private final PhaseEvidenceRepository evidence;
    public WorkflowLocks(ProjectRepository projects, ProjectPhaseRepository phases,
            DisbursementRepository payments, PhaseEvidenceRepository evidence) {
        this.projects=projects; this.phases=phases; this.payments=payments; this.evidence=evidence;
    }
    public Project project(Long id) {
        return projects.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Project not found."));
    }
    public ProjectPhase phase(Long id) {
        Long projectId = phases.findParentId(id).orElseThrow(() -> new IllegalArgumentException("Phase not found."));
        project(projectId);
        return phases.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Phase not found."));
    }
    public Disbursement payment(Long id) {
        phase(payments.findParentId(id).orElseThrow(() -> new IllegalArgumentException("Payment not found.")));
        return payments.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Payment not found."));
    }
    public PhaseEvidence evidence(Long id) {
        phase(evidence.findParentId(id).orElseThrow(() -> new IllegalArgumentException("Evidence not found.")));
        return evidence.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Evidence not found."));
    }
    public static void requireActive(Project project) {
        if (!"ACTIVE".equals(project.getStatus()))
            throw new IllegalArgumentException("This action is only available for an active, approved project.");
    }
    public static void checkVersion(long version) {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
            String expected = servlet.getRequest().getHeader("If-Match");
            if (expected != null && !expected.equals(Long.toString(version)) && !expected.equals("\"" + version + "\""))
                throw new Conflict("This record was updated by another user. Refresh it before performing another action.");
        }
    }
    public static class Conflict extends IllegalArgumentException {
        public Conflict(String message) { super(message); }
    }
}
