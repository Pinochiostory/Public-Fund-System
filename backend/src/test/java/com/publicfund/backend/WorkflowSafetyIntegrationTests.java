package com.publicfund.backend;

import com.publicfund.backend.entity.*;
import com.publicfund.backend.repository.*;
import com.publicfund.backend.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Explicit opt-in. Never runs against the application database. */
@EnabledIfEnvironmentVariable(named="PUBLIC_FUND_MYSQL_SAFETY_TEST",matches="true")
@SpringBootTest(properties={
    "app.jwt.secret=test-only-signing-key-not-for-deployment-123456789",
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/public_fund_workflow_test",
    "spring.jpa.hibernate.ddl-auto=create-drop", "PUBLIC_FUND_RETURN_DESTINATION=TEST FUNDER ONLY - NOT A REAL ACCOUNT", "spring.jpa.show-sql=false",
    "app.bootstrap-admin.full-name=","app.bootstrap-admin.email=","app.bootstrap-admin.password=",
    "app.upload-root=${java.io.tmpdir}/public-fund-workflow-test-files",
    "logging.level.org.springframework.security.config.annotation.authentication.configuration.InitializeUserDetailsBeanManagerConfigurer=ERROR",
    "logging.level.org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration=ERROR"
})
@AutoConfigureMockMvc
class WorkflowSafetyIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired FundingApplicationRepository applications;
    @Autowired ProjectRepository projects;
    @Autowired ProjectPhaseRepository phases;
    @Autowired DisbursementRepository payments;
    @Autowired PhaseEvidenceRepository evidence;
    @Autowired NotificationRepository notifications;
    @Autowired AuditLogRepository audit;
    @Autowired EmailVerificationRepository codes;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    @MockitoBean EmailDeliveryService mail;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean FileStorageService fileStore;
    User admin,provider,other,officer;
    String a,p,o,v;
    @BeforeEach void accounts() {
        String suffix=UUID.randomUUID().toString().substring(0,8);
        admin=user("ADMIN",suffix+"a");provider=user("SERVICE_PROVIDER",suffix+"p");
        other=user("SERVICE_PROVIDER",suffix+"o");officer=user("GOVERNMENT_OFFICER",suffix+"v");
        a=bearer(admin);p=bearer(provider);o=bearer(other);v=bearer(officer);
    }
    User user(String role,String email) {
        User user=new User("Safety Test "+role,email+"@example.test",encoder.encode("Test-Only@123"),role);
        return users.saveAndFlush(user);
    }
    String bearer(User u) { return "Bearer "+jwt.generateAccountToken(u); }
    FundingApplication application() {
        return applications.saveAndFlush(new FundingApplication(provider.getId(),"Safety project "+UUID.randomUUID(),"Testing public benefit",new BigDecimal("100.00"),"One month"));
    }
    Project project() {
        FundingApplication app=application();app.setStatus("APPROVED");applications.saveAndFlush(app);
        return projects.saveAndFlush(new Project(app.getId(),provider.getId(),app.getTitle(),app.getDescription(),app.getRequestedBudget(),app.getTimeline()));
    }
    ProjectPhase phase(Project project,String status,int order,String amount) {
        ProjectPhase phase=new ProjectPhase(project.getId(),"Phase "+order,"Test phase",new BigDecimal(amount),order);
        phase.setStatus(status);
        if(Set.of("IN_PROGRESS","EVIDENCE_APPROVED","COMPLETED").contains(status))phase.setStartedAt(LocalDateTime.now().minusDays(3));
        return phases.saveAndFlush(phase);
    }
    Disbursement payment(ProjectPhase phase) {
        return payments.saveAndFlush(new Disbursement(phase.getProjectId(),phase.getId(),phase.getAllocatedBudget()));
    }
    long uploaded(ProjectPhase phase,String token) throws Exception {
        var result=mvc.perform(multipart("/api/evidence/phase/"+phase.getId())
                .file(pdf()).header("Authorization",token)).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    MockMultipartFile pdf() {
        return new MockMultipartFile("file","invoice.pdf","application/pdf","%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes());
    }

    @Test void ownershipAndRolesAreEnforcedForManualRequests() throws Exception {
        Project project=project();ProjectPhase phase=phase(project,"FUNDED",1,"100");
        mvc.perform(post("/api/project-phases/project/"+project.getId()).header("Authorization",o)
            .param("phaseName","Intrusion").param("description","No").param("allocatedBudget","1").param("phaseOrder","2"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/project-phases/"+phase.getId()+"/start").header("Authorization",o)).andExpect(status().isForbidden());
        mvc.perform(get("/api/projects/"+project.getId()+"/summary").header("Authorization",o)).andExpect(status().isForbidden());
        mvc.perform(get("/api/projects/"+project.getId()+"/summary").header("Authorization",p)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").header("Authorization",p)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization",v)).andExpect(status().isForbidden());
        mvc.perform(post("/api/applications/"+project.getApplicationId()+"/approve").header("Authorization",v)).andExpect(status().isForbidden());
        mvc.perform(get("/api/evidence").header("Authorization",a)).andExpect(status().isOk());
        mvc.perform(get("/api/evidence").header("Authorization",p)).andExpect(status().isForbidden());
    }

    @Test void concurrentDoublePaymentOnlyCommitsOnce() throws Exception {
        Project project=project();ProjectPhase phase=phase(project,"APPROVED",1,"100");Disbursement payment=payment(phase);
        List<Integer> statuses=race(
            ()->mvc.perform(post("/api/disbursements/"+payment.getId()+"/release").header("Authorization",a).param("paymentReference","BANK-1")).andReturn().getResponse().getStatus(),
            ()->mvc.perform(post("/api/disbursements/"+payment.getId()+"/release").header("Authorization",a).param("paymentReference","BANK-2")).andReturn().getResponse().getStatus());
        assertEquals(1,statuses.stream().filter(s->s==200).count());
        assertEquals(1,statuses.stream().filter(s->s>=400&&s<500).count());
        assertEquals("RELEASED",payments.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals("FUNDED",phases.findById(phase.getId()).orElseThrow().getStatus());
        assertEquals(1,audit.findByProjectIdOrApplicationIdOrderByCreatedAtAsc(project.getId(),project.getApplicationId()).stream().filter(l->"DISBURSEMENT_RELEASED".equals(l.getAction())).count());
        mvc.perform(post("/api/disbursements/phase/"+phase.getId()).header("Authorization",a)).andExpect(status().isBadRequest());
    }

    @Test void concurrentDifferentPaymentsCannotExceedProjectBudget() throws Exception {
        Project project=project();
        // Simulate inconsistent legacy proposals; financial protection must still be authoritative.
        Disbursement first=payment(phase(project,"APPROVED",1,"60"));
        Disbursement second=payment(phase(project,"APPROVED",1,"60"));
        List<Integer> statuses=race(
            ()->mvc.perform(post("/api/disbursements/"+first.getId()+"/release").header("Authorization",a).param("paymentReference","BANK-A")).andReturn().getResponse().getStatus(),
            ()->mvc.perform(post("/api/disbursements/"+second.getId()+"/release").header("Authorization",a).param("paymentReference","BANK-B")).andReturn().getResponse().getStatus());
        assertEquals(1,statuses.stream().filter(s->s==200).count());
        assertEquals(1,statuses.stream().filter(s->s==400).count());
        BigDecimal released=payments.findByProjectId(project.getId()).stream().filter(x->"RELEASED".equals(x.getStatus())).map(Disbursement::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        assertEquals(0,released.compareTo(new BigDecimal("60")));
        assertTrue(notifications.findByUserIdOrderByCreatedAtDesc(admin.getId()).stream().anyMatch(n->n.getTitle().equals("Insufficient project funds")));
    }
    List<Integer> race(Callable<Integer> first,Callable<Integer> second) throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
            var futures=List.of(first,second).stream().map(task->pool.submit(()->{ready.countDown();go.await();return task.call();})).toList();
            assertTrue(ready.await(5,TimeUnit.SECONDS));go.countDown();
            return List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS));
        }
    }

    @Test void rejectionNeedsReasonAndStaleAdminCannotReverseDecision() throws Exception {
        FundingApplication app=application();
        mvc.perform(post("/api/applications/"+app.getId()+"/reject").header("Authorization",a).param("rejectionReason","  ")).andExpect(status().isBadRequest());
        assertEquals("PENDING",applications.findById(app.getId()).orElseThrow().getStatus());
        mvc.perform(post("/api/applications/"+app.getId()+"/reject").header("Authorization",a).header("If-Match","0")
                .param("rejectionReason","Budget details are missing.")).andExpect(status().isOk());
        FundingApplication rejected=applications.findById(app.getId()).orElseThrow();
        assertEquals("Budget details are missing.",rejected.getRejectionReason());assertNotNull(rejected.getRejectedAt());assertEquals(admin.getEmail(),rejected.getRejectedBy());
        mvc.perform(post("/api/applications/"+app.getId()+"/approve").header("Authorization",a).header("If-Match","0")).andExpect(status().isConflict());
        Notification notification=notifications.findByUserIdOrderByCreatedAtDesc(provider.getId()).get(0);
        mvc.perform(post("/api/notifications/"+notification.getId()+"/read").header("Authorization",p)).andExpect(status().isOk());
        assertTrue(notifications.findById(notification.getId()).orElseThrow().isReadStatus());
        assertTrue(notification.getMessage().contains("Budget details are missing."));
    }

    @Test void rejectedEvidenceCanBeResubmittedAndCompletionCannotBeOverwritten() throws Exception {
        Project project=project();ProjectPhase phase=phase(project,"IN_PROGRESS",1,"100");
        long first=uploaded(phase,p);
        mvc.perform(multipart("/api/evidence/phase/"+phase.getId()).file(pdf()).header("Authorization",p)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/evidence/"+first+"/reject").header("Authorization",a).param("verificationComment"," ")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/evidence/"+first+"/reject").header("Authorization",v).param("verificationComment","Invoice does not match.")).andExpect(status().isOk());
        long corrected=uploaded(phase,p);
        assertNotEquals(first,corrected);assertEquals(2,evidence.findByPhaseId(phase.getId()).size());
        mvc.perform(post("/api/evidence/"+corrected+"/approve").header("Authorization",a)).andExpect(status().isOk());
        mvc.perform(post("/api/project-phases/"+phase.getId()+"/conclude").header("Authorization",a)).andExpect(status().isOk());
        var completed=phases.findById(phase.getId()).orElseThrow().getCompletedAt();assertNotNull(completed);
        mvc.perform(post("/api/project-phases/"+phase.getId()+"/conclude").header("Authorization",a)).andExpect(status().isBadRequest());
        assertEquals(completed,phases.findById(phase.getId()).orElseThrow().getCompletedAt());
        mvc.perform(post("/api/evidence/"+corrected+"/reject").header("Authorization",a).param("verificationComment","Late reversal")).andExpect(status().isBadRequest());
        assertEquals("REJECTED",evidence.findById(first).orElseThrow().getStatus());
    }

    @Test void workflowAndInvalidFileFailuresDoNotChangeRecords() throws Exception {
        Project project=project();ProjectPhase phase=phase(project,"PENDING_APPROVAL",1,"100");
        mvc.perform(post("/api/project-phases/"+phase.getId()+"/start").header("Authorization",p)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/project-phases/"+phase.getId()+"/conclude").header("Authorization",a)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/disbursements/phase/"+phase.getId()).header("Authorization",a)).andExpect(status().isBadRequest());
        phase.setStatus("IN_PROGRESS");phase.setStartedAt(LocalDateTime.now());phases.saveAndFlush(phase);
        mvc.perform(multipart("/api/evidence/phase/"+phase.getId()).file(new MockMultipartFile("file","fake.pdf","application/pdf","not a pdf".getBytes())).header("Authorization",p)).andExpect(status().isBadRequest());
        assertTrue(evidence.findByPhaseId(phase.getId()).isEmpty());
        assertTrue(payments.findByProjectId(project.getId()).isEmpty());
    }

    @Test void proofIsScopedAndCannotReplaceFinancialHistory() throws Exception {
        Project project=project();ProjectPhase phase=phase(project,"APPROVED",1,"100");Disbursement payment=payment(phase);
        mvc.perform(multipart("/api/disbursements/"+payment.getId()+"/proof").file(pdf()).header("Authorization",a)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/disbursements/"+payment.getId()+"/release").param("paymentReference","BANK-REF").header("Authorization",a)).andExpect(status().isOk());
        mvc.perform(multipart("/api/disbursements/"+payment.getId()+"/proof").file(pdf()).header("Authorization",a)).andExpect(status().isOk()).andExpect(jsonPath("$.proofPath").doesNotExist());
        mvc.perform(get("/api/disbursements/"+payment.getId()+"/proof").header("Authorization",p)).andExpect(status().isOk());
        mvc.perform(get("/api/disbursements/"+payment.getId()+"/proof").header("Authorization",o)).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/disbursements/"+payment.getId()+"/proof").file(pdf()).header("Authorization",a)).andExpect(status().isBadRequest());
        assertEquals("BANK-REF",payments.findById(payment.getId()).orElseThrow().getPaymentReference());
    }

    @Test void verificationCodeIsHashedSingleUseAndBlocksAccessUntilVerified() throws Exception {
        String email=UUID.randomUUID()+"@example.test";
        mvc.perform(post("/api/auth/register").param("fullName","New provider").param("email",email).param("password","Test-Only@123").param("role","SERVICE_PROVIDER"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.codeSent").value(true));
        User user=users.findByEmail(email).orElseThrow();assertFalse(user.isEmailVerified());
        var body=org.mockito.ArgumentCaptor.forClass(String.class);verify(mail).send(eq(email),anyString(),body.capture());
        String otp=body.getValue().replaceAll("(?s).*code is ([0-9]{6}).*","$1");
        EmailVerification stored=codes.findById(user.getId()).orElseThrow();
        assertTrue(encoder.matches(otp,stored.codeHash));assertNotEquals(otp,stored.codeHash);
        mvc.perform(get("/api/projects/my").header("Authorization",bearer(user))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").param("email",email).param("password","Test-Only@123")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/verify-email").param("email",email).param("code","bad")).andExpect(status().isBadRequest()).andExpect(content().string("The verification code is incorrect."));
        mvc.perform(post("/api/auth/verify-email").param("email",email).param("code",otp)).andExpect(status().isOk());
        assertTrue(users.findById(user.getId()).orElseThrow().isEmailVerified());assertNull(codes.findById(user.getId()).orElseThrow().codeHash);
        mvc.perform(post("/api/auth/verify-email").param("email",email).param("code",otp)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").param("email",email).param("password","Test-Only@123")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/register").param("fullName","Duplicate").param("email",email).param("password","Test-Only@123").param("role","SERVICE_PROVIDER")).andExpect(status().isBadRequest());
    }

    @Test void expiredCodeRateLimitsAndMailFailureAreTruthful() throws Exception {
        provider.setEmailVerified(false);users.saveAndFlush(provider);
        doThrow(new EmailDeliveryService.DeliveryFailure("The email could not be sent.")).when(mail).send(anyString(),anyString(),anyString());
        mvc.perform(post("/api/auth/resend-verification").param("email",provider.getEmail())).andExpect(status().isBadRequest()).andExpect(content().string("The email could not be sent."));
        EmailVerification code=codes.findById(provider.getId()).orElseThrow();assertNull(code.codeHash);
        mvc.perform(post("/api/auth/resend-verification").param("email",provider.getEmail())).andExpect(status().isBadRequest()).andExpect(content().string("Please wait 60 seconds before requesting another code."));
        code.codeHash=encoder.encode("123456");code.expiresAt=LocalDateTime.now().minusMinutes(1);codes.saveAndFlush(code);
        mvc.perform(post("/api/auth/verify-email").param("email",provider.getEmail()).param("code","123456")).andExpect(status().isBadRequest()).andExpect(content().string("This verification code has expired. Request a new code."));
        code.expiresAt=LocalDateTime.now().plusMinutes(5);code.attempts=5;codes.saveAndFlush(code);
        mvc.perform(post("/api/auth/verify-email").param("email",provider.getEmail()).param("code","123456")).andExpect(status().isBadRequest());
        assertFalse(users.findById(provider.getId()).orElseThrow().isEmailVerified());
    }


    @Test void duplicateApplicationsAndPhaseSubmissionsAreSerialized() throws Exception {
        String title="Concurrent "+UUID.randomUUID();
        List<Integer> submitted=race(
            ()->mvc.perform(post("/api/applications").header("Authorization",p).param("title",title).param("description","Same submission").param("requestedBudget","100").param("timeline","One month")).andReturn().getResponse().getStatus(),
            ()->mvc.perform(post("/api/applications").header("Authorization",p).param("title",title).param("description","Same submission").param("requestedBudget","100").param("timeline","One month")).andReturn().getResponse().getStatus());
        assertEquals(1,submitted.stream().filter(x->x==200).count());
        Project project=project();
        List<Integer> proposed=race(
            ()->mvc.perform(post("/api/project-phases/project/"+project.getId()).header("Authorization",p).param("phaseName","First").param("description","Same phase").param("allocatedBudget","100").param("phaseOrder","1")).andReturn().getResponse().getStatus(),
            ()->mvc.perform(post("/api/project-phases/project/"+project.getId()).header("Authorization",p).param("phaseName","First").param("description","Same phase").param("allocatedBudget","100").param("phaseOrder","1")).andReturn().getResponse().getStatus());
        assertEquals(1,proposed.stream().filter(x->x==200).count());
        assertEquals(1,phases.findByProjectId(project.getId()).size());
    }

    @Test void rejectedProjectCannotStartAndMissingEvidenceCannotComplete() throws Exception {
        Project project=project();ProjectPhase phase=phase(project,"FUNDED",1,"100");
        project.setStatus("COMPLETED");project=projects.saveAndFlush(project);
        mvc.perform(post("/api/project-phases/"+phase.getId()+"/start").header("Authorization",p)).andExpect(status().isBadRequest());
        project.setStatus("ACTIVE");projects.saveAndFlush(project);
        phase.setStatus("EVIDENCE_APPROVED");phase.setStartedAt(LocalDateTime.now());phases.saveAndFlush(phase);
        mvc.perform(post("/api/project-phases/"+phase.getId()+"/conclude").header("Authorization",a)).andExpect(status().isBadRequest());
        assertNull(phases.findById(phase.getId()).orElseThrow().getCompletedAt());
    }

    @Test void completionPreservesRemainingFundsAndNotifiesAdministrators() throws Exception {
        Project project=project();project.setStatus("PENDING_COMPLETION");projects.saveAndFlush(project);
        ProjectPhase phase=phase(project,"COMPLETED",1,"60");phase.setCompletedAt(LocalDateTime.now());phases.saveAndFlush(phase);
        Disbursement payment=payment(phase);payment.setStatus("RELEASED");payment.setReleasedAt(LocalDateTime.now());payments.saveAndFlush(payment);
        reconcileAllSpent(project,"60");
        mvc.perform(post("/api/projects/"+project.getId()+"/complete").header("Authorization",a)).andExpect(status().isOk());
        mvc.perform(get("/api/projects/"+project.getId()+"/summary").header("Authorization",p)).andExpect(status().isOk()).andExpect(jsonPath("$.remainingBalance").value(40));
        assertEquals(1,payments.findByProjectId(project.getId()).size());
        assertTrue(notifications.findByUserIdOrderByCreatedAtDesc(admin.getId()).stream().anyMatch(n->n.getTitle().equals("Completed project with remaining funds")));
    }

    @Test void evidenceWithdrawalRetainsOldFileAndAllowsCorrection() throws Exception {
        Project project=project();ProjectPhase phase=phase(project,"IN_PROGRESS",1,"100");long first=uploaded(phase,p);
        mvc.perform(post("/api/evidence/"+first+"/withdraw").header("Authorization",o).param("reason","Wrong invoice")).andExpect(status().isForbidden());
        mvc.perform(post("/api/evidence/"+first+"/withdraw").header("Authorization",p).param("reason","Wrong invoice")).andExpect(status().isOk());
        uploaded(phase,p);
        assertEquals(2,evidence.findByPhaseId(phase.getId()).size());
        mvc.perform(get("/api/evidence/"+first+"/file").header("Authorization",p)).andExpect(status().isOk());
        assertEquals("WITHDRAWN",evidence.findById(first).orElseThrow().getStatus());
    }

    @Test void allEarlierPhasesMustBeCompletedBeforeStart() throws Exception {
        Project project=project();
        ProjectPhase earlier=phase(project,"PENDING_APPROVAL",1,"20");
        phase(project,"COMPLETED",2,"20");
        ProjectPhase next=phase(project,"FUNDED",3,"20");
        mvc.perform(post("/api/project-phases/"+next.getId()+"/start").header("Authorization",p))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.containsString("Phase 1")));
        assertNull(phases.findById(next.getId()).orElseThrow().getStartedAt());
        earlier.setStatus("COMPLETED"); phases.saveAndFlush(earlier);
        mvc.perform(post("/api/project-phases/"+next.getId()+"/start").header("Authorization",p)).andExpect(status().isOk());
        mvc.perform(post("/api/project-phases/"+next.getId()+"/start").header("Authorization",p)).andExpect(status().isBadRequest());
    }

    @Test void underwayAndVerifiedButUnfinishedPhasesBlockStart() throws Exception {
        for(String state:List.of("IN_PROGRESS","EVIDENCE_APPROVED")) {
            Project project=project();phase(project,state,1,"40");
            ProjectPhase next=phase(project,"FUNDED",2,"40");
            mvc.perform(post("/api/project-phases/"+next.getId()+"/start").header("Authorization",p)).andExpect(status().isBadRequest());
        }
    }

    @Test void concurrentStartsSerializeAtTheProject() throws Exception {
        Project project=project();
        ProjectPhase first=phase(project,"FUNDED",1,"40"), second=phase(project,"FUNDED",2,"40");
        var results=race(
            ()->mvc.perform(post("/api/project-phases/"+first.getId()+"/start").header("Authorization",p)).andReturn().getResponse().getStatus(),
            ()->mvc.perform(post("/api/project-phases/"+second.getId()+"/start").header("Authorization",p)).andReturn().getResponse().getStatus());
        assertEquals(1,results.stream().filter(code->code==200).count());
        assertEquals("IN_PROGRESS",phases.findById(first.getId()).orElseThrow().getStatus());
        assertEquals("FUNDED",phases.findById(second.getId()).orElseThrow().getStatus());
    }

    Project closeoutFixture(String amount) throws Exception {
        Project project=project();project.setStatus("PENDING_COMPLETION");project=projects.saveAndFlush(project);
        ProjectPhase phase=phase(project,"COMPLETED",1,amount);
        Disbursement payment=payment(phase);payment.setStatus("RELEASED");payments.saveAndFlush(payment);
        if(new BigDecimal(amount).compareTo(project.getApprovedBudget())<=0) reconcileAllSpent(project,amount);
        return project;
    }

    @Test void closeoutRequiresIndependentAdminAndPreservesSnapshotAndAudit() throws Exception {
        Project project=closeoutFixture("60");
        String endpoint="/api/projects/"+project.getId()+"/closeout-review";
        mvc.perform(post("/api/projects/"+project.getId()+"/complete").header("Authorization",a)).andExpect(status().isOk());
        Project closed=projects.findById(project.getId()).orElseThrow();
        assertEquals(0,new BigDecimal("40").compareTo(closed.getCloseoutRetainedAmount()));
        assertEquals(admin.getId(),closed.getCloseoutInitiatedBy());
        for(String token:List.of(a,p,v)) mvc.perform(post(endpoint).header("Authorization",token)
            .param("reference","FUND-1").param("note","Reconciled")).andExpect(status().isForbidden());
        String second=bearer(user("ADMIN",UUID.randomUUID().toString()));
        mvc.perform(post(endpoint).header("Authorization",second).param("reference"," ").param("note","Reason")).andExpect(status().isBadRequest());
        mvc.perform(post(endpoint).header("Authorization",second).param("reference","FUND-1").param("note"," ")).andExpect(status().isBadRequest());
        mvc.perform(post(endpoint).header("Authorization",second).header("If-Match","0")
            .param("reference","FUND-1").param("note","Reconciled")).andExpect(status().isConflict());
        mvc.perform(post(endpoint).header("Authorization",second).header("If-Match",String.valueOf(closed.getVersion()))
            .param("reference","FUND-1").param("note","Undisbursed allocation reconciled with funder ledger")).andExpect(status().isOk());
        mvc.perform(post(endpoint).header("Authorization",second).param("reference","OVERWRITE").param("note","No"))
            .andExpect(status().isBadRequest());
        Project reviewed=projects.findById(project.getId()).orElseThrow();
        assertNotNull(reviewed.getCloseoutReviewedAt()); assertNotEquals(admin.getId(),reviewed.getCloseoutReviewedBy());
        assertEquals("FUND-1",reviewed.getCloseoutReference());
        assertEquals(1,payments.findByProjectId(project.getId()).size());
        assertEquals(1,audit.findByProjectIdOrApplicationIdOrderByCreatedAtAsc(project.getId(),project.getApplicationId())
            .stream().filter(log->"PROJECT_CLOSEOUT_REVIEWED".equals(log.getAction())).count());
        mvc.perform(get("/api/projects/"+project.getId()+"/summary").header("Authorization",p))
            .andExpect(status().isOk()).andExpect(jsonPath("$.project.closeoutRetainedAmount").value(40));
        Disbursement extra=payment(phase(project,"APPROVED",2,"40"));
        mvc.perform(post("/api/disbursements/"+extra.getId()+"/release").header("Authorization",a)
            .param("paymentReference","DIVERT")).andExpect(status().isBadRequest());
    }

    @Test void closeoutRefusesOverspendingAndUnsettledPayments() throws Exception {
        Project over=closeoutFixture("120");
        mvc.perform(post("/api/projects/"+over.getId()+"/complete").header("Authorization",a)).andExpect(status().isBadRequest());
        assertNull(projects.findById(over.getId()).orElseThrow().getCloseoutRetainedAmount());
        Project pending=closeoutFixture("60");
        payment(phase(pending,"COMPLETED",2,"10"));
        mvc.perform(post("/api/projects/"+pending.getId()+"/complete").header("Authorization",a)).andExpect(status().isBadRequest());
        assertEquals("PENDING_COMPLETION",projects.findById(pending.getId()).orElseThrow().getStatus());
    }

    @Test void concurrentCloseoutReviewsOnlyRecordOneAndZeroBalanceNeedsNoReview() throws Exception {
        Project project=closeoutFixture("60");
        mvc.perform(post("/api/projects/"+project.getId()+"/complete").header("Authorization",a)).andExpect(status().isOk());
        String second=bearer(user("ADMIN",UUID.randomUUID().toString()));
        String third=bearer(user("ADMIN",UUID.randomUUID().toString()));
        String endpoint="/api/projects/"+project.getId()+"/closeout-review";
        var results=race(
            ()->mvc.perform(post(endpoint).header("Authorization",second).param("reference","R2").param("note","Reviewed")).andReturn().getResponse().getStatus(),
            ()->mvc.perform(post(endpoint).header("Authorization",third).param("reference","R3").param("note","Reviewed")).andReturn().getResponse().getStatus());
        assertEquals(1,results.stream().filter(code->code==200).count());
        Project spent=closeoutFixture("100");
        mvc.perform(post("/api/projects/"+spent.getId()+"/complete").header("Authorization",a)).andExpect(status().isOk());
        mvc.perform(post("/api/projects/"+spent.getId()+"/closeout-review").header("Authorization",second)
            .param("reference","ZERO").param("note","Reviewed")).andExpect(status().isBadRequest());
    }

    MockMultipartFile batchFile(String name, String content) {
        return new MockMultipartFile("files", name, "application/pdf",
                ("%PDF-1.4\\n" + content + "\\n%%EOF").getBytes());
    }

    @Test void multipleFilesShareSubmissionAndEveryFileMustBeApproved() throws Exception {
        Project project=project(); ProjectPhase phase=phase(project,"IN_PROGRESS",1,"100");
        String endpoint="/api/evidence/phase/"+phase.getId()+"/batch";
        mvc.perform(multipart(endpoint).file(batchFile("invoice.pdf","invoice")).file(batchFile("report.pdf","report"))
            .header("Authorization",p)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        var items=evidence.findByPhaseId(phase.getId());
        assertEquals(2,items.size()); assertNotNull(items.get(0).getSubmissionId());
        assertEquals(items.get(0).getSubmissionId(),items.get(1).getSubmissionId());
        mvc.perform(post("/api/evidence/"+items.get(0).getId()+"/approve").header("Authorization",a)).andExpect(status().isOk());
        assertEquals("IN_PROGRESS",phases.findById(phase.getId()).orElseThrow().getStatus());
        mvc.perform(post("/api/project-phases/"+phase.getId()+"/conclude").header("Authorization",a)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/evidence/"+items.get(1).getId()+"/approve").header("Authorization",v)).andExpect(status().isOk());
        assertEquals("EVIDENCE_APPROVED",phases.findById(phase.getId()).orElseThrow().getStatus());
        mvc.perform(post("/api/project-phases/"+phase.getId()+"/conclude").header("Authorization",a)).andExpect(status().isOk());
    }

    @Test void rejectedSetRequiresCompleteResubmissionAndKeepsHistory() throws Exception {
        Project project=project();ProjectPhase phase=phase(project,"IN_PROGRESS",1,"100");
        String endpoint="/api/evidence/phase/"+phase.getId()+"/batch";
        mvc.perform(multipart(endpoint).file(batchFile("invoice.pdf","original")).file(batchFile("report.pdf","report"))
            .header("Authorization",p)).andExpect(status().isOk());
        var old=evidence.findByPhaseId(phase.getId());
        mvc.perform(post("/api/evidence/"+old.get(0).getId()+"/reject").param("verificationComment","Correct the invoice amount")
            .header("Authorization",a)).andExpect(status().isOk());
        mvc.perform(multipart(endpoint).file(batchFile("corrected.pdf","corrected")).header("Authorization",p)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/evidence/"+old.get(1).getId()+"/approve").header("Authorization",a)).andExpect(status().isOk());
        assertEquals("IN_PROGRESS",phases.findById(phase.getId()).orElseThrow().getStatus());
        mvc.perform(multipart(endpoint).file(batchFile("corrected.pdf","corrected")).file(batchFile("report.pdf","report"))
            .header("Authorization",p)).andExpect(status().isOk());
        var history=evidence.findByPhaseId(phase.getId());
        assertEquals(4,history.size());
        var latest=EvidenceSubmissions.latest(history);
        assertEquals(2,latest.size());
        for(var item:latest) mvc.perform(post("/api/evidence/"+item.getId()+"/approve").header("Authorization",a)).andExpect(status().isOk());
        assertEquals("EVIDENCE_APPROVED",phases.findById(phase.getId()).orElseThrow().getStatus());
        assertEquals("REJECTED",evidence.findById(old.get(0).getId()).orElseThrow().getStatus());
    }

    @Test void invalidDuplicateAndUnauthorizedBatchLeavesNoEvidence() throws Exception {
        Project project=project();ProjectPhase phase=phase(project,"IN_PROGRESS",1,"100");
        String endpoint="/api/evidence/phase/"+phase.getId()+"/batch";
        for(String token:List.of(o,a,v)) mvc.perform(multipart(endpoint).file(batchFile("test.pdf","test"))
            .header("Authorization",token)).andExpect(status().isForbidden());
        mvc.perform(multipart(endpoint).file(batchFile("one.pdf","same")).file(batchFile("two.pdf","same"))
            .header("Authorization",p)).andExpect(status().isBadRequest());
        mvc.perform(multipart(endpoint).file(batchFile("good.pdf","good"))
            .file(new MockMultipartFile("files","bad.pdf","application/pdf","not a PDF".getBytes()))
            .header("Authorization",p)).andExpect(status().isBadRequest());
        var tooMany=multipart(endpoint);
        for(int i=0;i<11;i++)tooMany.file(batchFile("file"+i+".pdf","different"+i));
        mvc.perform(tooMany.header("Authorization",p)).andExpect(status().isBadRequest());
        assertTrue(evidence.findByPhaseId(phase.getId()).isEmpty());
    }

    @Test void batchStorageFailureRollsBackRowsAuditAndPreviouslyWrittenFiles() throws Exception {
        Project project=project();ProjectPhase phase=phase(project,"IN_PROGRESS",1,"100");
        var stored=new java.util.concurrent.atomic.AtomicReference<String>();
        doAnswer(invocation -> {
            if(stored.get()!=null)throw new IllegalArgumentException("Storage unavailable. No evidence was submitted.");
            String path=(String)invocation.callRealMethod(); stored.set(path); return path;
        }).when(fileStore).storeEvidenceFile(any());
        mvc.perform(multipart("/api/evidence/phase/"+phase.getId()+"/batch")
            .file(batchFile("one.pdf","one")).file(batchFile("two.pdf","two")).header("Authorization",p)).andExpect(status().isBadRequest());
        assertNotNull(stored.get());
        assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of(stored.get())));
        assertTrue(evidence.findByPhaseId(phase.getId()).isEmpty());
        assertTrue(audit.findByProjectIdOrApplicationIdOrderByCreatedAtAsc(project.getId(),project.getApplicationId()).isEmpty());
    }

    @Test void simultaneousBatchSubmissionsOnlySaveOneSet() throws Exception {
        Project project=project();ProjectPhase phase=phase(project,"IN_PROGRESS",1,"100");
        String endpoint="/api/evidence/phase/"+phase.getId()+"/batch";
        Callable<Integer> upload=()->mvc.perform(multipart(endpoint)
            .file(batchFile("one.pdf","one")).file(batchFile("two.pdf","two")).header("Authorization",p))
            .andReturn().getResponse().getStatus();
        var statuses=race(upload,upload);
        assertEquals(1,statuses.stream().filter(code->code==200).count());
        assertEquals(2,evidence.findByPhaseId(phase.getId()).size());
    }

    @Test void failedOptionalEmailDoesNotUndoRejection() throws Exception {
        FundingApplication app=application();when(mail.isConfigured()).thenReturn(true);
        doThrow(new EmailDeliveryService.DeliveryFailure("Delivery failed")).when(mail).send(anyString(),anyString(),anyString());
        mvc.perform(post("/api/applications/"+app.getId()+"/reject").header("Authorization",a).param("rejectionReason","Missing budget")).andExpect(status().isOk());
        assertEquals("REJECTED",applications.findById(app.getId()).orElseThrow().getStatus());
        assertFalse(notifications.findByUserIdOrderByCreatedAtDesc(provider.getId()).isEmpty());
        assertFalse(audit.findByActionOrderByCreatedAtDesc("NOTIFICATION_EMAIL_FAILED").isEmpty());
    }
    @Autowired ProjectReconciliationRepository financialRecords;
    @Autowired ReconciliationService reconciliation;

    String reconciliationPath(Project project) { return "/api/projects/"+project.getId()+"/reconciliation"; }
    long declareSpend(Project project,String amount) throws Exception {
        var response=mvc.perform(multipart(reconciliationPath(project)+"/declarations").file(pdf())
            .header("Authorization",p).header("If-Match",String.valueOf(projects.findById(project.getId()).orElseThrow().getVersion()))
            .param("amount",amount).param("note","Final expenditure supported by receipts"))
            .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).get("id").asLong();
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder financialReview(Project project,long id,String token,boolean approved,String receipt) {
        long version=financialRecords.findById(id).orElseThrow().getVersion();
        return post(reconciliationPath(project)+"/"+id+"/review").header("Authorization",token)
            .header("If-Match",String.valueOf(version)).param("approve",String.valueOf(approved))
            .param("reason",approved?"Checked supporting documents / funder ledger":"The figures do not match the receipts")
            .param("receiptReference",receipt);
    }
    void reconcileAllSpent(Project project,String amount) throws Exception {
        long declaration=declareSpend(project,amount);
        mvc.perform(financialReview(project,declaration,a,true,"")).andExpect(status().isOk());
    }
    Project financialFixture() {
        Project project=project();project.setStatus("PENDING_COMPLETION");project=projects.saveAndFlush(project);
        ProjectPhase phase=phase(project,"COMPLETED",1,"25");
        Disbursement payment=payment(phase);payment.setStatus("RELEASED");payment.setReleasedAt(LocalDateTime.now());payments.saveAndFlush(payment);
        return project;
    }
    org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder returnClaim(Project project,long declaration,String amount,String ref) throws Exception {
        var summary=mvc.perform(get(reconciliationPath(project)).header("Authorization",p)).andExpect(status().isOk()).andReturn();
        String destinationVersion=json.readTree(summary.getResponse().getContentAsString()).get("destinationVersion").asText();
        return multipart(reconciliationPath(project)+"/returns").file(pdf()).header("Authorization",p)
            .header("If-Match",String.valueOf(financialRecords.findById(declaration).orElseThrow().getVersion()))
            .param("declarationId",String.valueOf(declaration)).param("amount",amount).param("note","Unspent advance returned to official funder")
            .param("reference",ref).param("transferredOn",java.time.LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg")).toString())
            .param("destinationVersion",destinationVersion);
    }
    long saveReturn(Project project,long declaration,String amount,String ref) throws Exception {
        var response=mvc.perform(returnClaim(project,declaration,amount,ref)).andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test void reconciliationRejectsInvalidMoneyRolesFilesAndWorkflow() throws Exception {
        Project project=financialFixture();
        for(String token:List.of(a,o,v)) mvc.perform(multipart(reconciliationPath(project)+"/declarations").file(pdf())
            .header("Authorization",token).header("If-Match","0").param("amount","20").param("note","Valid note")).andExpect(status().isForbidden());
        for(String value:List.of("-1","25.01","0.001","999999999999999999999.99"))
            mvc.perform(multipart(reconciliationPath(project)+"/declarations").file(pdf()).header("Authorization",p)
                .header("If-Match",String.valueOf(project.getVersion())).param("amount",value).param("note","Invalid amount")).andExpect(status().isBadRequest());
        mvc.perform(multipart(reconciliationPath(project)+"/declarations")
            .file(new MockMultipartFile("file","not-real.pdf","application/pdf","bad".getBytes())).header("Authorization",p)
            .header("If-Match",String.valueOf(project.getVersion())).param("amount","20").param("note","Bad file")).andExpect(status().isBadRequest());
        assertTrue(financialRecords.findByProjectIdOrderByIdAsc(project.getId()).isEmpty());
        Project active=project();phase(active,"IN_PROGRESS",1,"25");
        mvc.perform(multipart(reconciliationPath(active)+"/declarations").file(pdf()).header("Authorization",p)
            .header("If-Match","0").param("amount","0").param("note","Too early")).andExpect(status().isBadRequest());
        mvc.perform(get(reconciliationPath(project)).header("Authorization",o)).andExpect(status().isForbidden());
        mvc.perform(get(reconciliationPath(project)).header("Authorization",v)).andExpect(status().isForbidden());
        mvc.perform(get(reconciliationPath(project))).andExpect(status().isUnauthorized());
    }
    @Test void declarationRejectionRetainsHistoryAndRequiresVersions() throws Exception {
        Project project=financialFixture();
        mvc.perform(multipart(reconciliationPath(project)+"/declarations").file(pdf()).header("Authorization",p)
            .param("amount","20").param("note","No version")).andExpect(status().isPreconditionRequired());
        long id=declareSpend(project,"20");
        mvc.perform(multipart(reconciliationPath(project)+"/declarations").file(pdf()).header("Authorization",p)
            .header("If-Match",String.valueOf(project.getVersion())).param("amount","20").param("note","Duplicate")).andExpect(status().isBadRequest());
        mvc.perform(post(reconciliationPath(project)+"/"+id+"/review").header("Authorization",a).param("approve","false").param("reason","Reason"))
            .andExpect(status().isPreconditionRequired());
        mvc.perform(financialReview(project,id,p,false,"")).andExpect(status().isForbidden());
        mvc.perform(financialReview(project,id,v,false,"")).andExpect(status().isForbidden());
        mvc.perform(post(reconciliationPath(project)+"/"+id+"/review").header("Authorization",a).header("If-Match","0")
            .param("approve","false").param("reason"," ")).andExpect(status().isBadRequest());
        mvc.perform(financialReview(project,id,a,false,"")).andExpect(status().isOk());
        long corrected=declareSpend(project,"21");
        assertEquals(2,financialRecords.findByProjectIdOrderByIdAsc(project.getId()).size());
        mvc.perform(get(reconciliationPath(project)+"/"+id+"/file").header("Authorization",p)).andExpect(status().isOk());
        mvc.perform(get(reconciliationPath(project)+"/"+id+"/file").header("Authorization",o)).andExpect(status().isForbidden());
        mvc.perform(post(reconciliationPath(project)+"/"+corrected+"/review").header("Authorization",a).header("If-Match","88")
            .param("approve","true").param("reason","Review")).andExpect(status().isConflict());
        mvc.perform(financialReview(project,corrected,a,true,"")).andExpect(status().isOk());
        mvc.perform(financialReview(project,corrected,a,false,"")).andExpect(status().isBadRequest());
        assertFalse(audit.findByActionOrderByCreatedAtDesc("EXPENDITURE_REJECTED").isEmpty());
    }
    @Test void expenditureAndReturnAreSeparateAndClosureWaitsForIndependentReceipt() throws Exception {
        Project project=financialFixture();
        mvc.perform(post("/api/projects/"+project.getId()+"/complete").header("Authorization",a)).andExpect(status().isBadRequest());
        long declaration=declareSpend(project,"20");
        mvc.perform(returnClaim(project,declaration,"5","FIRST")).andExpect(status().isBadRequest());
        mvc.perform(financialReview(project,declaration,a,true,"")).andExpect(status().isOk());
        mvc.perform(get(reconciliationPath(project)).header("Authorization",p)).andExpect(jsonPath("$.outstandingReturn").value(5))
            .andExpect(jsonPath("$.undisbursedAmount").value(75)).andExpect(jsonPath("$.status").value("RETURN_DUE"));
        for(String amount:List.of("0","-1","5.01","0.001")) mvc.perform(returnClaim(project,declaration,amount,"BAD")).andExpect(status().isBadRequest());
        long returned=saveReturn(project,declaration,"5","TRANSFER-5");
        mvc.perform(returnClaim(project,declaration,"5","TRANSFER-5")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/projects/"+project.getId()+"/complete").header("Authorization",a)).andExpect(status().isBadRequest());
        mvc.perform(financialReview(project,returned,a,true,"RECEIPT-5")).andExpect(status().isForbidden());
        String second=bearer(user("ADMIN",UUID.randomUUID().toString()));
        mvc.perform(financialReview(project,returned,second,true," ")).andExpect(status().isBadRequest());
        mvc.perform(financialReview(project,returned,second,true,"RECEIPT-"+UUID.randomUUID())).andExpect(status().isOk());
        mvc.perform(get(reconciliationPath(project)).header("Authorization",p)).andExpect(jsonPath("$.status").value("RECONCILED"))
            .andExpect(jsonPath("$.verifiedReturns").value(5)).andExpect(jsonPath("$.outstandingReturn").value(0))
            .andExpect(jsonPath("$.releasedAmount").value(25)).andExpect(jsonPath("$.undisbursedAmount").value(75))
            .andExpect(jsonPath("$.history[0].filePath").doesNotExist()).andExpect(jsonPath("$.history[1].receiptKey").doesNotExist());
        mvc.perform(post("/api/projects/"+project.getId()+"/complete").header("Authorization",a)).andExpect(status().isOk());
        Project closed=projects.findById(project.getId()).orElseThrow();
        assertEquals(0,closed.getCloseoutReleasedAmount().compareTo(new BigDecimal("25")));
        assertEquals(0,closed.getCloseoutRetainedAmount().compareTo(new BigDecimal("75")));
        assertEquals(1,payments.findByProjectId(project.getId()).size());
        mvc.perform(returnClaim(project,declaration,"1","EXTRA")).andExpect(status().isBadRequest());
    }
    @Test void partialReturnsAndRejectedProofKeepHistoryAndDoNotClearBalance() throws Exception {
        Project project=financialFixture();long declaration=declareSpend(project,"20");
        mvc.perform(financialReview(project,declaration,a,true,"")).andExpect(status().isOk());
        String second=bearer(user("ADMIN",UUID.randomUUID().toString()));
        long bad=saveReturn(project,declaration,"2","PART-1");
        mvc.perform(financialReview(project,bad,second,false,"")).andExpect(status().isOk());
        mvc.perform(get(reconciliationPath(project)).header("Authorization",p)).andExpect(jsonPath("$.verifiedReturns").value(0)).andExpect(jsonPath("$.outstandingReturn").value(5));
        long corrected=saveReturn(project,declaration,"2","PART-1");
        mvc.perform(financialReview(project,corrected,second,true,"R-"+UUID.randomUUID())).andExpect(status().isOk());
        mvc.perform(get(reconciliationPath(project)).header("Authorization",p)).andExpect(jsonPath("$.outstandingReturn").value(3)).andExpect(jsonPath("$.status").value("RETURN_DUE"));
        long last=saveReturn(project,declaration,"3","PART-2");
        mvc.perform(financialReview(project,last,second,true,"R-"+UUID.randomUUID())).andExpect(status().isOk());
        assertEquals(4,financialRecords.findByProjectIdOrderByIdAsc(project.getId()).size());
        mvc.perform(get(reconciliationPath(project)+"/"+bad+"/file").header("Authorization",p)).andExpect(status().isOk());
    }
    @Test void concurrentDeclarationsReturnsAndReviewsCommitOnlyOnce() throws Exception {
        Project project=financialFixture();
        Callable<Integer> declaration=()->mvc.perform(multipart(reconciliationPath(project)+"/declarations").file(pdf()).header("Authorization",p)
            .header("If-Match",String.valueOf(project.getVersion())).param("amount","20").param("note","Race")).andReturn().getResponse().getStatus();
        assertEquals(1,race(declaration,declaration).stream().filter(s->s==200).count());
        long id=financialRecords.findByProjectIdOrderByIdAsc(project.getId()).getFirst().getId();
        mvc.perform(financialReview(project,id,a,true,"")).andExpect(status().isOk());
        var request1=returnClaim(project,id,"5","RACE-1");var request2=returnClaim(project,id,"5","RACE-2");
        assertEquals(1,race(()->mvc.perform(request1).andReturn().getResponse().getStatus(),()->mvc.perform(request2).andReturn().getResponse().getStatus()).stream().filter(s->s==200).count());
        long returned=financialRecords.findByProjectIdOrderByIdAsc(project.getId()).getLast().getId();
        String second=bearer(user("ADMIN",UUID.randomUUID().toString())),third=bearer(user("ADMIN",UUID.randomUUID().toString()));
        var review1=financialReview(project,returned,second,true,"REC-"+UUID.randomUUID());
        var review2=financialReview(project,returned,third,true,"REC-"+UUID.randomUUID());
        assertEquals(1,race(()->mvc.perform(review1).andReturn().getResponse().getStatus(),()->mvc.perform(review2).andReturn().getResponse().getStatus()).stream().filter(s->s==200).count());
    }
    @Test void oneBankReceiptCannotBeCountedTwiceAcrossProjects() throws Exception {
        String receipt="UNIQUE-"+UUID.randomUUID(),second=bearer(user("ADMIN",UUID.randomUUID().toString()));
        for(int i=0;i<2;i++){
            Project project=financialFixture();long declaration=declareSpend(project,"20");
            mvc.perform(financialReview(project,declaration,a,true,"")).andExpect(status().isOk());
            long returned=saveReturn(project,declaration,"5","RETURN");
            mvc.perform(financialReview(project,returned,second,true,receipt)).andExpect(i==0?status().isOk():status().isConflict());
            if(i==1)assertEquals("PENDING",financialRecords.findById(returned).orElseThrow().getStatus());
        }
    }
    @Test void missingDestinationAndFailedStorageCannotClaimSuccess() throws Exception {
        Project project=financialFixture();
        doThrow(new IllegalArgumentException("Storage unavailable")).when(fileStore).storeReconciliationFile(any());
        mvc.perform(multipart(reconciliationPath(project)+"/declarations").file(pdf()).header("Authorization",p)
            .header("If-Match",String.valueOf(project.getVersion())).param("amount","20").param("note","Final")).andExpect(status().isBadRequest());
        assertTrue(financialRecords.findByProjectIdOrderByIdAsc(project.getId()).isEmpty());
        doCallRealMethod().when(fileStore).storeReconciliationFile(any());
        long declaration=declareSpend(project,"20");mvc.perform(financialReview(project,declaration,a,true,"")).andExpect(status().isOk());
        Object target=org.springframework.test.util.AopTestUtils.getTargetObject(reconciliation);
        String original=(String)org.springframework.test.util.ReflectionTestUtils.getField(target,"destination");
        try{
            org.springframework.test.util.ReflectionTestUtils.setField(target,"destination","");
            mvc.perform(returnClaim(project,declaration,"5","NONE")).andExpect(status().isBadRequest());
        }finally{org.springframework.test.util.ReflectionTestUtils.setField(target,"destination",original);}
        assertEquals(1,financialRecords.findByProjectIdOrderByIdAsc(project.getId()).size());
    }

}

