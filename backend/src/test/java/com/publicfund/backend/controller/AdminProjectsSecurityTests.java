package com.publicfund.backend.controller;

import com.publicfund.backend.config.SecurityConfig;
import com.publicfund.backend.entity.User;
import com.publicfund.backend.repository.UserRepository;
import com.publicfund.backend.security.JwtAuthenticationFilter;
import com.publicfund.backend.service.*;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ProjectController.class, ReconciliationController.class})
@org.springframework.test.context.TestPropertySource(properties = "app.jwt.secret=test-only-signing-key-not-for-deployment-123456789")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
class AdminProjectsSecurityTests {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean UserRepository users;
    @MockitoBean ProjectService projects;
    @MockitoBean ReconciliationService reconciliation;
    @MockitoBean FileStorageService files;

    private User account(String role) {
        User user = new User("Demo User", "demo@example.test", "test-password-hash", role);
        user.setId(42L);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        return user;
    }

    private String bearer(User user) { return "Bearer " + jwt.generateAccountToken(user); }

    @Test void adminCanLoadProjectsAndReconciliationWithTheSameSession() throws Exception {
        String token = bearer(account("ADMIN"));
        when(projects.getAllProjects()).thenReturn(List.of());
        when(reconciliation.summary(6L)).thenReturn(new ReconciliationService.Summary(
                0L, BigDecimal.ZERO, new BigDecimal("100000"), null, BigDecimal.ZERO,
                null, BigDecimal.ZERO, "NOT_SUBMITTED", "", "test", List.of()));
        mvc.perform(get("/api/projects").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(get("/api/projects/6/reconciliation").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NOT_SUBMITTED"));
        mvc.perform(get("/api/projects").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test void internalErrorDispatchKeepsOriginalStatusInsteadOfExpiringSession() throws Exception {
        for (int originalStatus : new int[]{404, 500}) {
            mvc.perform(get("/error").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; })
                    .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, originalStatus)
                    .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/projects/6/reconciliation"))
                    .andExpect(status().is(originalStatus))
                    .andExpect(jsonPath("$.status").value(originalStatus));
        }
    }

    @Test void errorPathIsNotABypassForOrdinaryRequests() throws Exception {
        mvc.perform(get("/error")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/projects/6/reconciliation")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reconciliation);
    }

    @Test void unknownEndpointDoesNotInvalidateValidAdminSession() throws Exception {
        String token = bearer(account("ADMIN"));
        mvc.perform(get("/api/projects/6/not-an-endpoint").header("Authorization", token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/projects").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test void reconciliationFailureIsNotAnAuthenticationFailure() throws Exception {
        String token = bearer(account("ADMIN"));
        when(reconciliation.summary(6L)).thenThrow(new IllegalStateException("internal details"));
        mvc.perform(get("/api/projects/6/reconciliation").header("Authorization", token))
                .andExpect(status().isInternalServerError());
        mvc.perform(get("/api/projects").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test void officerStillCannotReadFinancialReconciliation() throws Exception {
        mvc.perform(get("/api/projects/6/reconciliation").header("Authorization", bearer(account("GOVERNMENT_OFFICER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reconciliation);
    }

    @Test void invalidAndDisabledSessionsStillReturnUnauthorized() throws Exception {
        mvc.perform(get("/api/projects/6/reconciliation").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        User admin = account("ADMIN");
        String token = bearer(admin);
        admin.setAccountStatus("DISABLED");
        mvc.perform(get("/api/projects/6/reconciliation").header("Authorization", token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reconciliation);
    }
}
