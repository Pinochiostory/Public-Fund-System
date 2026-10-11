package com.publicfund.backend.service;

import com.publicfund.backend.entity.*;
import com.publicfund.backend.repository.*;
import com.publicfund.backend.security.ProjectAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ReconciliationService {
    private final ProjectReconciliationRepository records;
    private final DisbursementRepository payments;
    private final ProjectPhaseRepository phases;
    private final WorkflowLocks locks;
    private final ProjectAccess access;
    private final FileStorageService files;
    private final AuditLogService audit;
    private final NotificationService notifications;
    private final String destination;

    public ReconciliationService(ProjectReconciliationRepository records, DisbursementRepository payments,
            ProjectPhaseRepository phases, WorkflowLocks locks, ProjectAccess access, FileStorageService files,
            AuditLogService audit, NotificationService notifications,
            @Value("${PUBLIC_FUND_RETURN_DESTINATION:}") String destination) {
        this.records=records;this.payments=payments;this.phases=phases;this.locks=locks;
        this.access=access;this.files=files;this.audit=audit;this.notifications=notifications;
        this.destination=destination.trim();
        if(this.destination.length()>1500) throw new IllegalArgumentException("Return destination configuration is too long.");
    }

    public record Summary(long projectVersion, BigDecimal releasedAmount, BigDecimal undisbursedAmount,
            BigDecimal approvedExpenditure, BigDecimal verifiedReturns, BigDecimal outstandingReturn,
            BigDecimal pendingReturn, String status, String returnDestination, String destinationVersion,
            List<ProjectReconciliation> history) {}

    public Summary summary(Long projectId) {
        Project project=locks.project(projectId);access.requireReader(project);
        return snapshot(project);
    }
    private Summary snapshot(Project project) {
        var history=records.findByProjectIdOrderByIdAsc(project.getId());
        BigDecimal released=released(project.getId());
        var declaration=latestDeclaration(history);
        BigDecimal spent=declaration!=null && "APPROVED".equals(declaration.getStatus()) ? declaration.getAmount() : null;
        BigDecimal returned=sumReturns(history,"APPROVED");
        BigDecimal pending=sumReturns(history,"PENDING");
        BigDecimal outstanding=spent==null ? null : released.subtract(spent).subtract(returned);
        String status=declaration==null ? "NOT_SUBMITTED" : switch(declaration.getStatus()) {
            case "REJECTED" -> "CHANGES_REQUIRED";
            case "PENDING" -> "PENDING_REVIEW";
            default -> outstanding.signum()==0 ? "RECONCILED" : pending.signum()>0 ? "RETURN_UNDER_REVIEW" : "RETURN_DUE";
        };
        if(declaration!=null && released.compareTo(declaration.getReleasedAmount())!=0 || outstanding!=null && outstanding.signum()<0)
            status="INVESTIGATION_REQUIRED";
        return new Summary(project.getVersion(),released,project.getApprovedBudget().subtract(released),
                spent,returned,outstanding,pending,status,destination,destinationVersion(),history);
    }
    // Called under the same project lock by the existing completion workflow.
    public void requireReconciled(Project project) {
        if (!"RECONCILED".equals(snapshot(project).status()))
            throw new IllegalArgumentException("Reconcile actual expenditure and verify all unspent funds returned to the funder before completing this project.");
    }

    public ProjectReconciliation declare(Long projectId, BigDecimal amount, String note, MultipartFile file, String expected) {
        Project project=locks.project(projectId);access.requireOwner(project);eligible(project);
        requireVersion(project.getVersion(),expected);
        var history=records.findByProjectIdOrderByIdAsc(projectId);
        var previous=latestDeclaration(history);
        if(previous!=null && !"REJECTED".equals(previous.getStatus()))
            throw new IllegalArgumentException("An expenditure declaration already exists. Wait for review; approved figures cannot be overwritten.");
        BigDecimal released=released(projectId);amount=money(amount,true);
        if(amount.compareTo(released)>0) throw new IllegalArgumentException("Declared expenditure cannot exceed the R"+released+" recorded as paid to you.");
        var record=base(project,"DECLARATION",amount,released,note,file);
        records.saveAndFlush(record);
        audit.logProjectAction(projectId,"EXPENDITURE_DECLARED","Declaration "+record.getId()+" | Spent: R"+amount+" | Paid: R"+released+" | Explanation: "+record.getNote());
        notifications.alertAdministrators("Expenditure review required","Review the final expenditure declaration for "+project.getTitle()+".");
        return record;
    }

    public ProjectReconciliation submitReturn(Long projectId, Long declarationId, BigDecimal amount, String note,
            String reference, LocalDate transferredOn, String destinationVersion, MultipartFile file, String expected) {
        Project project=locks.project(projectId);access.requireOwner(project);eligible(project);
        var history=records.findByProjectIdOrderByIdAsc(projectId);
        var declaration=latestDeclaration(history);
        if(declaration==null || !Objects.equals(declaration.getId(),declarationId) || !"APPROVED".equals(declaration.getStatus()))
            throw new IllegalArgumentException("Wait for Administrator approval of your expenditure declaration before recording a return.");
        requireVersion(declaration.getVersion(),expected);
        if(destination.isBlank()) throw new IllegalArgumentException("Official return-account instructions are not configured. Contact the system owner. Do not transfer funds to an Administrator's personal account.");
        if(!destinationVersion().equals(destinationVersion)) throw new WorkflowLocks.Conflict("Return instructions have changed. Refresh and confirm the official destination.");
        if(history.stream().anyMatch(r->"RETURN".equals(r.getKind()) && "PENDING".equals(r.getStatus())))
            throw new IllegalArgumentException("A return is already awaiting independent review. Do not submit it again.");
        BigDecimal released=released(projectId);unchanged(declaration,released);
        amount=money(amount,false);
        BigDecimal outstanding=released.subtract(declaration.getAmount()).subtract(sumReturns(history,"APPROVED"));
        if(amount.compareTo(outstanding)>0) throw new IllegalArgumentException("Return amount exceeds the R"+outstanding+" still due. Contact the system owner if you transferred too much.");
        reference=text(reference,100,"Transfer reference");
        final String normalizedReference=reference;
        if(history.stream().anyMatch(r->"RETURN".equals(r.getKind()) && !"REJECTED".equals(r.getStatus()) && normalizedReference.equalsIgnoreCase(r.getTransferReference())))
            throw new IllegalArgumentException("This transfer reference has already been recorded.");
        if(transferredOn==null || transferredOn.isAfter(LocalDate.now(ZoneId.of("Africa/Johannesburg"))) || transferredOn.isBefore(project.getCreatedAt().toLocalDate()))
            throw new IllegalArgumentException("The transfer date must be between project creation and today.");
        var record=base(project,"RETURN",amount,released,note,file);
        record.setDeclarationId(declarationId);record.setTransferReference(reference);
        record.setTransferredOn(transferredOn);record.setReturnDestination(destination);
        records.saveAndFlush(record);
        audit.logProjectAction(projectId,"FUND_RETURN_SUBMITTED","Return claim "+record.getId()+" | R"+amount+" | Reference: "+reference+" | Awaiting independent bank/ledger verification. "+record.getNote());
        notifications.alertAdministrators("Fund return requires independent verification","Provider reports returning R"+amount+" for "+project.getTitle()+". A different Administrator from the expenditure reviewer must verify receipt.");
        return record;
    }

    public ProjectReconciliation review(Long projectId, Long id, boolean approve, String reason,
            String receiptReference, String expected) {
        Long reviewer=access.requireAdministratorId();
        Project project=locks.project(projectId);eligible(project);
        var record=record(projectId,id);
        requireVersion(record.getVersion(),expected);
        if(!"PENDING".equals(record.getStatus())) throw new IllegalArgumentException("This submission has already been reviewed. Refresh to see the decision.");
        if(Objects.equals(record.getSubmittedBy(),reviewer)) throw new org.springframework.security.access.AccessDeniedException("You cannot review your own submission.");
        reason=text(reason,2000,"Review explanation");
        var history=records.findByProjectIdOrderByIdAsc(projectId);
        var declaration=latestDeclaration(history);
        if("DECLARATION".equals(record.getKind())) {
            if(declaration==null || !Objects.equals(declaration.getId(),id)) throw new WorkflowLocks.Conflict("A newer declaration exists.");
        } else {
            if(declaration==null || !Objects.equals(record.getDeclarationId(),declaration.getId()) || !"APPROVED".equals(declaration.getStatus()))
                throw new IllegalArgumentException("This return does not belong to an approved expenditure declaration.");
            if(Objects.equals(declaration.getReviewedBy(),reviewer))
                throw new org.springframework.security.access.AccessDeniedException("A different Administrator must independently verify the return.");
            if(approve) {
                BigDecimal outstanding=record.getReleasedAmount().subtract(declaration.getAmount()).subtract(sumReturns(history,"APPROVED"));
                if(record.getAmount().compareTo(outstanding)>0) throw new IllegalArgumentException("This return exceeds the outstanding amount. Investigate before confirming receipt.");
                receiptReference=text(receiptReference,100,"Unique funder bank transaction / receipt reference");
                record.setReceiptReference(receiptReference);
                // DB uniqueness also protects two projects being reviewed concurrently.
                record.setReceiptKey(receiptReference.toUpperCase(Locale.ROOT));
            }
        }
        unchanged(record,released(projectId));
        if(approve) files.read(record.getFilePath());
        record.setStatus(approve?"APPROVED":"REJECTED");record.setReviewedBy(reviewer);
        record.setReviewedAt(LocalDateTime.now());record.setReviewNote(reason);
        records.saveAndFlush(record);
        String action=("RETURN".equals(record.getKind())?"FUND_RETURN_":"EXPENDITURE_")+(approve?"APPROVED":"REJECTED");
        audit.logProjectAction(projectId,action,"Record "+id+" | R"+record.getAmount()+" | "+reason+
                (record.getReceiptReference()==null?"":" | Funder receipt: "+record.getReceiptReference()));
        notifications.createNotification(project.getApplicantId(),approve?"Financial review recorded":"Financial submission requires correction",
                project.getTitle()+": "+("RETURN".equals(record.getKind())?"Return":"Expenditure")+" "+(approve?"approved":"rejected")+". Reason: "+reason+
                (approve && "RETURN".equals(record.getKind())?" Verified against funder records; no new payout is authorized.":""));
        return record;
    }

    public ProjectReconciliation downloadRecord(Long projectId, Long id) {
        Project project=locks.project(projectId);access.requireReader(project);return record(projectId,id);
    }
    private ProjectReconciliation record(Long projectId, Long id) {
        var record=records.findById(id).orElseThrow(()->new IllegalArgumentException("Financial submission not found."));
        if(!Objects.equals(record.getProjectId(),projectId)) throw new org.springframework.security.access.AccessDeniedException("Submission does not belong to this project.");
        return record;
    }
    private ProjectReconciliation base(Project project, String kind, BigDecimal amount, BigDecimal released, String note, MultipartFile file) {
        note=text(note,2000,"Explanation");
        String type=files.validate(file,false);
        var record=new ProjectReconciliation();
        record.setProjectId(project.getId());record.setKind(kind);record.setAmount(amount);record.setReleasedAmount(released);
        record.setStatus("PENDING");record.setSubmittedBy(project.getApplicantId());record.setSubmittedAt(LocalDateTime.now());record.setNote(note);
        record.setFileType(type);record.setFileSize(file.getSize());record.setFileName(files.displayName(file));
        record.setFilePath(files.storeReconciliationFile(file));
        return record;
    }
    private void eligible(Project project) {
        if(!Set.of("PENDING_COMPLETION","COMPLETED").contains(project.getStatus()))
            throw new IllegalArgumentException("Complete the phases and request project completion before submitting final expenditure.");
        var list=phases.findByProjectId(project.getId());
        if(list.stream().noneMatch(p->"COMPLETED".equals(p.getStatus())) ||
                list.stream().anyMatch(p->!Set.of("COMPLETED","REJECTED").contains(p.getStatus())))
            throw new IllegalArgumentException("All non-rejected phases must be completed before final expenditure can be reconciled.");
        if(payments.findByProjectId(project.getId()).stream().anyMatch(p->Set.of("PENDING","PROCESSING").contains(p.getStatus())))
            throw new IllegalArgumentException("Resolve pending payments before reconciling final expenditure.");
        if(released(project.getId()).compareTo(project.getApprovedBudget())>0)
            throw new IllegalArgumentException("Recorded payments exceed the approved budget. Administrative investigation is required.");
    }
    private BigDecimal released(Long id) {
        return payments.findByProjectId(id).stream().filter(p->"RELEASED".equals(p.getStatus())).map(Disbursement::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    private static ProjectReconciliation latestDeclaration(List<ProjectReconciliation> history) {
        return history.stream().filter(r->"DECLARATION".equals(r.getKind())).max(Comparator.comparing(ProjectReconciliation::getId)).orElse(null);
    }
    private static BigDecimal sumReturns(List<ProjectReconciliation> history,String status) {
        return history.stream().filter(r->"RETURN".equals(r.getKind()) && status.equals(r.getStatus())).map(ProjectReconciliation::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    private static void unchanged(ProjectReconciliation record,BigDecimal released) {
        if(record.getReleasedAmount().compareTo(released)!=0) throw new WorkflowLocks.Conflict("Recorded payments changed. Investigate before continuing reconciliation.");
    }
    private static BigDecimal money(BigDecimal amount,boolean zeroAllowed) {
        if(amount==null || amount.signum()<0 || (!zeroAllowed && amount.signum()==0) || amount.compareTo(new BigDecimal("9999999999999.99"))>0)
            throw new IllegalArgumentException(zeroAllowed?"Enter a non-negative expenditure amount.":"Enter a positive return amount.");
        try { return amount.setScale(2,RoundingMode.UNNECESSARY); }
        catch(ArithmeticException e) { throw new IllegalArgumentException("Amounts may have no more than two decimal places."); }
    }
    private static String text(String value,int max,String name) {
        if(value==null || value.isBlank() || value.trim().length()>max) throw new IllegalArgumentException(name+" must contain 1 to "+max+" characters.");
        return value.trim();
    }
    private static void requireVersion(long version,String expected) {
        if(expected==null || expected.isBlank()) throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,"Refresh the record before submitting; a version is required.");
        if(!expected.equals(String.valueOf(version)) && !expected.equals("\""+version+"\"")) throw new WorkflowLocks.Conflict("Record changed.");
    }
    private String destinationVersion() {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(destination.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("Return instructions cannot be validated."); }
    }
}

