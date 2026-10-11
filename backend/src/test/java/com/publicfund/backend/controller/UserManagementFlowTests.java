package com.publicfund.backend.controller;

import com.publicfund.backend.config.SecurityConfig;
import com.publicfund.backend.entity.User;
import com.publicfund.backend.entity.PasswordResetToken;
import com.publicfund.backend.repository.UserRepository;
import com.publicfund.backend.repository.PasswordResetTokenRepository;
import com.publicfund.backend.security.JwtAuthenticationFilter;
import com.publicfund.backend.service.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real security filter, JWTs, BCrypt, controllers and services; only persistence is mocked. */
@WebMvcTest({UserManagementController.class, AuthController.class, TestController.class})
@org.springframework.test.context.TestPropertySource(properties = "app.jwt.secret=test-only-signing-key-not-for-deployment-123456789")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, AuthService.class,
        AdminAuthService.class, PasswordResetService.class, RequiredPasswordChangeService.class, UserManagementService.class})
class UserManagementFlowTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.publicfund.backend.service.EmailVerificationService emailVerification;
    private static final String ORIGINAL = "Test-Original@123";
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @MockitoBean UserRepository users;
    @MockitoBean PasswordResetTokenRepository resetTokens;
    @MockitoBean AuditLogService audit;
    @MockitoBean EmailDeliveryService mail;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<Long, User> rows = new LinkedHashMap<>();
    private String adminToken;

    @BeforeEach void setup() {
        when(mail.isConfigured()).thenReturn(true);
        String hash = encoder.encode(ORIGINAL);
        rows.clear();
        add(1L, "ADMIN", hash); add(2L, "SERVICE_PROVIDER", hash);
        add(3L, "GOVERNMENT_OFFICER", hash); add(4L, "ADMIN", hash);
        when(users.findByEmail(anyString())).thenAnswer(call -> rows.values().stream().filter(u -> u.getEmail().equals(call.getArgument(0))).findFirst());
        when(users.findByEmailForUpdate(anyString())).thenAnswer(call -> users.findByEmail(call.getArgument(0)));
        when(users.findByIdForUpdate(anyLong())).thenAnswer(call -> Optional.ofNullable(rows.get(call.getArgument(0))));
        when(users.findAll()).thenAnswer(call -> new ArrayList<>(rows.values()));
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        adminToken = jwt.generateAccountToken(rows.get(1L));
    }

    private void add(long id, String role, String hash) {
        User user = new User("Person " + id, "person" + id + "@example.com", hash, role);
        user.setId(id); rows.put(id, user);
    }
    private String bearer(String token) { return "Bearer " + token; }
    private String rolePath(long id) { return id == 2 ? "provider" : id == 3 ? "officer" : "admin"; }
    private JsonNode login(long id, String password) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/login").param("email", rows.get(id).getEmail()).param("password", password))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode reset(long id) throws Exception {
        return json.readTree(mvc.perform(post("/api/admin/users/" + id + "/reset-password").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.user.password").doesNotExist()).andReturn().getResponse().getContentAsString());
    }
    private void setStatus(long id, String value, int expected) throws Exception {
        mvc.perform(patch("/api/admin/users/" + id + "/status").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + value + "\"}"))
                .andExpect(status().is(expected));
    }

    @Test void adminCanListAllRolesWithoutCredentialMaterial() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[1].role").value("SERVICE_PROVIDER"))
                .andExpect(jsonPath("$[2].role").value("GOVERNMENT_OFFICER"))
                .andExpect(jsonPath("$[*].password").isEmpty()).andExpect(jsonPath("$[*].temporaryPassword").isEmpty());
    }

    @ParameterizedTest @ValueSource(longs = {2, 3})
    void disabledUsersCannotLoginOrUseExistingTokenAndCanBeEnabled(long id) throws Exception {
        String token = jwt.generateAccountToken(rows.get(id));
        setStatus(id, "DISABLED", 200);
        mvc.perform(post("/api/auth/login").param("email", rows.get(id).getEmail()).param("password", ORIGINAL))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/test/" + rolePath(id)).header("Authorization", bearer(token))).andExpect(status().isUnauthorized());
        verify(audit).logAction(eq(1L), eq(rows.get(1L).getEmail()), eq("ADMIN"), eq("ADMIN_DISABLED_USER"), contains("ID " + id));
        setStatus(id, "ACTIVE", 200);
        String active = login(id, ORIGINAL).get("token").asText();
        mvc.perform(get("/api/test/" + rolePath(id)).header("Authorization", bearer(active))).andExpect(status().isOk());
        verify(audit).logAction(eq(1L), anyString(), eq("ADMIN"), eq("ADMIN_ENABLED_USER"), contains("ID " + id));
    }

    @Test void cannotDisableAnyAdminOrResetSelfAndBadRequestsAreRejected() throws Exception {
        setStatus(1, "DISABLED", 403); setStatus(4, "DISABLED", 403);
        setStatus(2, "ADMIN", 400); setStatus(99, "ACTIVE", 404);
        mvc.perform(post("/api/admin/users/1/reset-password").header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden());
        verify(users, never()).save(any());
        verifyNoInteractions(audit);
    }

    @ParameterizedTest @ValueSource(longs = {2, 3, 4})
    void resetRequiresNewPasswordBeforeAnyProtectedWorkflowAndInvalidatesOldSessions(long id) throws Exception {
        User target = rows.get(id);
        String oldToken = jwt.generateAccountToken(target);
        JsonNode result = reset(id);
        String temporary = result.get("temporaryPassword").asText();
        assertTrue(temporary.length() >= 28);
        assertTrue(encoder.matches(temporary, target.getPassword()));
        assertNotEquals(temporary, target.getPassword());
        assertTrue(target.isMustChangePassword());
        verify(resetTokens).deleteByUserId(id);
        mvc.perform(get("/api/test/" + rolePath(id)).header("Authorization", bearer(oldToken))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").param("email", target.getEmail()).param("password", ORIGINAL)).andExpect(status().isBadRequest());

        JsonNode temporaryLogin = login(id, temporary);
        assertTrue(temporaryLogin.get("user").get("mustChangePassword").asBoolean());
        String restrictedToken = temporaryLogin.get("token").asText();
        for (String path : new String[]{"/api/test/" + rolePath(id), "/api/test", "/api/admin/users"}) {
            mvc.perform(get(path).header("Authorization", bearer(restrictedToken))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        }
        mvc.perform(post("/api/auth/staff").header("Authorization", bearer(restrictedToken)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(restrictedToken))
                .param("temporaryPassword", temporary).param("newPassword", temporary)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(restrictedToken))
                .param("temporaryPassword", temporary).param("newPassword", "weak")).andExpect(status().isBadRequest());

        JsonNode changed = json.readTree(mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(restrictedToken))
                .param("temporaryPassword", temporary).param("newPassword", "New-Private@456"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andExpect(jsonPath("$.user.password").doesNotExist()).andReturn().getResponse().getContentAsString());
        assertTrue(encoder.matches("New-Private@456", target.getPassword()));
        assertFalse(target.isMustChangePassword());
        assertFalse(encoder.matches(temporary, target.getPassword()));
        mvc.perform(get("/api/test/" + rolePath(id)).header("Authorization", bearer(changed.get("token").asText())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/test").header("Authorization", bearer(restrictedToken))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/test").header("Authorization", bearer(oldToken))).andExpect(status().isUnauthorized());
        login(id, "New-Private@456");
        verify(audit).logAction(eq(1L), anyString(), eq("ADMIN"), eq("ADMIN_INITIATED_PASSWORD_RESET"), contains("ID " + id));
        verify(audit).logAction(eq(id), eq(target.getEmail()), eq(target.getRole()), eq("REQUIRED_PASSWORD_CHANGED"), anyString());
        for (var invocation : mockingDetails(audit).getInvocations()) {
            String logged = Arrays.toString(invocation.getArguments());
            assertFalse(logged.contains(temporary)); assertFalse(logged.contains("New-Private@456")); assertFalse(logged.contains(target.getPassword()));
        }
    }

    @ParameterizedTest @ValueSource(longs = {2, 3})
    void nonAdminsCannotCallAnyManagementEndpoint(long id) throws Exception {
        String token = jwt.generateAccountToken(rows.get(id));
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/users/4/status").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users/4/reset-password").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        verify(users, never()).save(any()); verifyNoInteractions(audit);
    }

    @Test void anonymousRequestsAreRejectedAndNormalSessionsCannotUseRequiredChangeEndpoint() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().is4xxClientError());
        mvc.perform(patch("/api/admin/users/2/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/admin/users/2/reset-password")).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(adminToken))
                .param("temporaryPassword", ORIGINAL).param("newPassword", "New-Private@456")).andExpect(status().isForbidden());
    }

    @Test void passwordResetNeverReenablesDisabledAccountAndOldPublicResetCannotBypassDisableOrRequiredChange() throws Exception {
        User provider = rows.get(2L);
        when(resetTokens.findByToken("old-reset")).thenReturn(Optional.of(new PasswordResetToken(2L, "old-reset", LocalDateTime.now().plusMinutes(20))));
        setStatus(2, "DISABLED", 200);
        String temporary = reset(2).get("temporaryPassword").asText();
        assertEquals("DISABLED", provider.getAccountStatus());
        mvc.perform(post("/api/auth/login").param("email", provider.getEmail()).param("password", temporary)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/forgot-password").param("email", provider.getEmail())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("resetToken"))));
        mvc.perform(post("/api/auth/reset-password").param("token", "old-reset").param("newPassword", "New-Private@456"))
                .andExpect(status().isBadRequest());
        assertEquals("DISABLED", provider.getAccountStatus());
        setStatus(2, "ACTIVE", 200);
        assertTrue(provider.isMustChangePassword());
        mvc.perform(post("/api/auth/reset-password").param("token", "old-reset").param("newPassword", "New-Private@456"))
                .andExpect(status().isBadRequest());
        assertTrue(login(2, temporary).get("user").get("mustChangePassword").asBoolean());
    }

    @Test void resettingTwiceReplacesTemporaryPasswordAndRestrictedSession() throws Exception {
        rows.get(3L).setAccountStatus("LOCKED");
        String first = reset(3).get("temporaryPassword").asText();
        String token = login(3, first).get("token").asText();
        String second = reset(3).get("temporaryPassword").asText();
        assertNotEquals(first, second);
        mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(token))
                .param("temporaryPassword", first).param("newPassword", "New-Private@456")).andExpect(status().isUnauthorized());
        assertTrue(encoder.matches(second, rows.get(3L).getPassword()));
        assertEquals("ACTIVE", rows.get(3L).getAccountStatus());
    }

    @Test void legacySyntheticOrUnboundTokensDoNotBypassCredentialRevocation() throws Exception {
        String legacy = jwt.generateToken(1L, rows.get(1L).getEmail(), "ADMINISTRATOR");
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(legacy))).andExpect(status().isUnauthorized());
    }
}
