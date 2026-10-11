package com.publicfund.backend.controller;

import com.publicfund.backend.config.SecurityConfig;
import com.publicfund.backend.entity.User;
import com.publicfund.backend.repository.UserRepository;
import com.publicfund.backend.security.JwtAuthenticationFilter;
import com.publicfund.backend.service.AdminAuthService;
import com.publicfund.backend.service.AuthService;
import com.publicfund.backend.service.JwtService;
import com.publicfund.backend.service.PasswordResetService;
import com.publicfund.backend.service.RequiredPasswordChangeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class StaffAccountSecurityTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.publicfund.backend.service.EmailVerificationService emailVerification;
    @Autowired MockMvc mvc;
    @MockitoBean AuthService auth;
    @MockitoBean AdminAuthService adminAuth;
    @MockitoBean PasswordResetService reset;
    @MockitoBean RequiredPasswordChangeService requiredChange;
    @MockitoBean JwtService jwt;
    @MockitoBean UserRepository users;

    private MockHttpServletRequestBuilder create(String role) {
        return post("/api/auth/staff").param("fullName", "Test Staff")
                .param("email", "staff@example.com").param("password", "Test-Only@123").param("role", role);
    }

    @Test void anonymousProviderAndOfficerCannotCreateStaff() throws Exception {
        mvc.perform(create("ADMIN")).andExpect(status().is4xxClientError());
        mvc.perform(create("ADMIN").with(user("provider").roles("SERVICE_PROVIDER")))
                .andExpect(status().isForbidden());
        mvc.perform(create("GOVERNMENT_OFFICER").with(user("officer").roles("VERIFICATION_OFFICER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auth);
    }

    @Test void adminCanCreateBothStaffRolesWithoutPasswordInResponse() throws Exception {
        for (String role : new String[]{"ADMIN", "GOVERNMENT_OFFICER"}) {
            User staff = new User("Test Staff", "staff@example.com", "sensitive-hash", role);
            staff.setId(47L);
            when(auth.createStaffUser("Test Staff", "staff@example.com", "Test-Only@123", role)).thenReturn(staff);
            mvc.perform(create(role).with(user("admin").roles("ADMINISTRATOR")))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(47))
                    .andExpect(jsonPath("$.role").value(role)).andExpect(jsonPath("$.fullName").value("Test Staff"))
                    .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.token").doesNotExist());
        }
    }

    @Test void validationAndDatabaseConflictsHaveSafeResponses() throws Exception {
        when(auth.createStaffUser(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Email already registered"))
                .thenThrow(new DataIntegrityViolationException("SQL password=sensitive"))
                .thenThrow(new IllegalStateException("password=sensitive"));
        mvc.perform(create("ADMIN").with(user("admin").roles("ADMINISTRATOR")))
                .andExpect(status().isBadRequest()).andExpect(content().string("Email already registered"));
        mvc.perform(create("ADMIN").with(user("admin").roles("ADMINISTRATOR")))
                .andExpect(status().isConflict()).andExpect(content().string("An account already exists with these details."));
        mvc.perform(create("ADMIN").with(user("admin").roles("ADMINISTRATOR")))
                .andExpect(status().isInternalServerError()).andExpect(content().string("Unable to create the account."));
    }

    @Test void existingLoginResponseKeepsCompatibleDashboardRolesAndRealIds() throws Exception {
        for (String role : new String[]{"ADMIN", "GOVERNMENT_OFFICER", "SERVICE_PROVIDER"}) {
            User staff = new User("Test Staff", "staff@example.com", "sensitive-hash", role);
            staff.setId(47L);
            when(auth.loginUser("staff@example.com", "Test-Only@123")).thenReturn(new AuthService.LoginResult(staff, "jwt"));
            mvc.perform(post("/api/auth/login").param("email", "staff@example.com").param("password", "Test-Only@123"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(47))
                    .andExpect(jsonPath("$.user.role").value(role.equals("ADMIN") ? "ADMINISTRATOR" :
                            role.equals("GOVERNMENT_OFFICER") ? "VERIFICATION_OFFICER" : "SERVICE_PROVIDER"))
                    .andExpect(jsonPath("$.user.password").doesNotExist()).andExpect(jsonPath("$.token").value("jwt"));
        }
    }
}
