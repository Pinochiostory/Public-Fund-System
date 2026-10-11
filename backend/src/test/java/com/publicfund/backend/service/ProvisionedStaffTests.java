package com.publicfund.backend.service;

import com.publicfund.backend.entity.User;
import com.publicfund.backend.repository.UserRepository;
import com.publicfund.backend.security.AccountRoles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProvisionedStaffTests {
    private static final String PASSWORD = "Test-Only@123";
    private final UserRepository users = mock(UserRepository.class);
    private final JwtService jwt = mock(JwtService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AuthService auth = new AuthService(users, encoder, jwt);

    private User account(String role) {
        User user = new User("Test Person", "person@example.com", encoder.encode(PASSWORD), role);
        user.setId(42L);
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        return user;
    }

    @Test void rejectsEveryPublicStaffRoleBeforeDatabaseAccess() {
        for (String role : new String[]{"ADMIN", "GOVERNMENT_OFFICER", "VERIFICATION_OFFICER",
                "ADMINISTRATOR", "", "other", "service_provider", "SERVICE_PROVIDER ", null}) {
            assertThrows(IllegalArgumentException.class, () ->
                auth.registerUser("Test", "person@example.com", PASSWORD, role));
        }
        verifyNoInteractions(users, jwt);
    }

    @Test void providersCanStillRegisterWithHashedPasswords() {
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        User result = auth.registerUser(" Test Provider ", "PROVIDER@example.com",
            PASSWORD, "SERVICE_PROVIDER");
        assertEquals("SERVICE_PROVIDER", result.getRole());
        assertEquals("Test Provider", result.getFullName());
        assertEquals("provider@example.com", result.getEmail());
        assertTrue(encoder.matches(PASSWORD, result.getPassword()));
        assertNotEquals(PASSWORD, result.getPassword());
        assertEquals("ACTIVE", result.getAccountStatus());
        assertEquals(0, result.getFailedLoginAttempts());
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "GOVERNMENT_OFFICER"})
    void createsCanonicalStaffInExistingRepository(String role) {
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        User result = auth.createStaffUser(" Test Staff ", " STAFF@EXAMPLE.COM ", PASSWORD, role);
        assertEquals(role, result.getRole());
        assertEquals("Test Staff", result.getFullName());
        assertEquals("staff@example.com", result.getEmail());
        assertEquals("ACTIVE", result.getAccountStatus());
        assertEquals(0, result.getFailedLoginAttempts());
        assertTrue(encoder.matches(PASSWORD, result.getPassword()));
        verify(users).save(result);
        verifyNoInteractions(jwt);
    }

    @Test void staffCreationRejectsUnexpectedRolesAndInvalidFields() {
        for (String role : new String[]{"SERVICE_PROVIDER", "ADMINISTRATOR", "VERIFICATION_OFFICER", "", null}) {
            assertThrows(IllegalArgumentException.class, () -> auth.createStaffUser("Name", "a@example.com", PASSWORD, role));
        }
        for (String name : new String[]{"", " ", "a".repeat(256), null}) {
            assertThrows(IllegalArgumentException.class, () -> auth.createStaffUser(name, "a@example.com", PASSWORD, "ADMIN"));
        }
        for (String email : new String[]{"", "bad", "a".repeat(256) + "@example.com", null}) {
            assertThrows(IllegalArgumentException.class, () -> auth.createStaffUser("Name", email, PASSWORD, "ADMIN"));
        }
        for (String password : new String[]{"short", "abcdefghij", "ABCDEFGHIJ", "LongPassword", "LongPassword1", null}) {
            assertThrows(IllegalArgumentException.class, () -> auth.createStaffUser("Name", "a@example.com", password, "ADMIN"));
        }
        verifyNoInteractions(users, jwt);
    }

    @Test void duplicateAccountIsNeverOverwrittenOrPromoted() {
        when(users.existsByEmail("person@example.com")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () ->
            auth.createStaffUser("Staff", " PERSON@example.com ", PASSWORD, "ADMIN"));
        assertThrows(IllegalArgumentException.class, () ->
            auth.registerUser("Provider", "person@example.com", PASSWORD, "SERVICE_PROVIDER"));
        verify(users, never()).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"SERVICE_PROVIDER", "ADMIN", "GOVERNMENT_OFFICER", "ADMINISTRATOR", "VERIFICATION_OFFICER"})
    void allDatabaseRolesUseExistingLoginWithCompatibleToken(String role) {
        User user = account(role);
        String loginRole = AccountRoles.loginRole(role);
        when(jwt.generateAccountToken(user)).thenReturn("test-token");
        var result = auth.loginUser(" PERSON@EXAMPLE.COM ", PASSWORD);
        assertSame(user, result.getUser());
        assertEquals("test-token", result.getToken());
        assertEquals(role, user.getRole());
        verify(jwt).generateAccountToken(user);
    }

    @Test void oldAdminLoginEndpointDelegatesToDatabaseLogin() {
        User user = account("ADMIN");
        var result = new AdminAuthService(auth).loginAdmin(user.getEmail(), PASSWORD);
        assertEquals(42L, result.getUser().getId());
        assertEquals("ADMINISTRATOR", result.getUser().getRole());
        assertEquals("Test Person", result.getUser().getFullName());
    }

    @ParameterizedTest @ValueSource(strings = {"SERVICE_PROVIDER", "GOVERNMENT_OFFICER"})
    void compatibilityAdminEndpointDoesNotAdmitOtherRoles(String role) {
        account(role);
        assertThrows(IllegalArgumentException.class, () -> auth.loginAdmin("person@example.com", PASSWORD));
        verifyNoInteractions(jwt);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "GOVERNMENT_OFFICER", "SERVICE_PROVIDER"})
    void lockoutAndRecoveryCounterBehaviorIsPreservedForAllRoles(String role) {
        User user = account(role);
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThrows(IllegalArgumentException.class, () -> auth.loginUser(user.getEmail(), "wrong"));
            assertEquals(attempt, user.getFailedLoginAttempts());
        }
        assertEquals("LOCKED", user.getAccountStatus());
        assertThrows(IllegalArgumentException.class, () -> auth.loginUser(user.getEmail(), PASSWORD));
        verifyNoInteractions(jwt);
    }

    @Test void successfulLoginClearsFailuresAndInactiveOrUnknownAccountsCannotLogin() {
        User user = account("ADMIN");
        user.setFailedLoginAttempts(2);
        auth.loginUser(user.getEmail(), PASSWORD);
        assertEquals(0, user.getFailedLoginAttempts());
        verify(users).save(user);
        clearInvocations(jwt);
        user.setAccountStatus("DISABLED");
        assertThrows(IllegalArgumentException.class, () -> auth.loginUser(user.getEmail(), PASSWORD));
        user.setAccountStatus("ACTIVE");
        user.setRole("UNRECOGNIZED");
        assertThrows(IllegalArgumentException.class, () -> auth.loginUser(user.getEmail(), PASSWORD));
        assertThrows(IllegalArgumentException.class, () -> auth.loginUser("missing@example.com", PASSWORD));
        verifyNoInteractions(jwt);
    }
}
