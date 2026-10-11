package com.publicfund.backend.security;

import com.publicfund.backend.entity.User;
import com.publicfund.backend.repository.UserRepository;
import com.publicfund.backend.service.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseStaffTokenTests {
    private final UserRepository users = mock(UserRepository.class);
    private final JwtService jwt = new JwtService("test-only-signing-key-not-for-deployment-123456789");
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, users);

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void signingKeyMustBeProvidedAndStrongEnough() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService(null));
        assertThrows(IllegalArgumentException.class, () -> new JwtService(""));
        assertThrows(IllegalArgumentException.class, () -> new JwtService("short"));
    }

    private User account(String role) {
        User user = new User("Person", "person@example.com", "unused", role);
        user.setId(42L);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        return user;
    }

    private Authentication authenticate(String token) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "GOVERNMENT_OFFICER", "SERVICE_PROVIDER", "ADMINISTRATOR", "VERIFICATION_OFFICER"})
    void realDatabaseUserGetsExistingWorkflowAuthority(String role) throws Exception {
        User user = account(role);
        Authentication result = authenticate(jwt.generateAccountToken(user));
        assertNotNull(result);
        assertEquals(user.getEmail(), result.getName());
        assertEquals("ROLE_" + AccountRoles.loginRole(role), result.getAuthorities().iterator().next().getAuthority());
    }

    @Test void syntheticOrMismatchedIdsAndElevatedRolesAreRejected() throws Exception {
        User user = account("GOVERNMENT_OFFICER");
        for (long id : new long[]{-1, 0, 99}) {
            assertNull(authenticate(jwt.generateToken(id, user.getEmail(), "VERIFICATION_OFFICER")));
        }
        assertNull(authenticate(jwt.generateToken(42L, user.getEmail(), "ADMINISTRATOR")));
        assertNull(authenticate(jwt.generateToken(42L, user.getEmail(), "UNKNOWN")));
    }

    @Test void deletedLockedAndDisabledUsersCannotUsePreviouslyIssuedTokens() throws Exception {
        User user = account("ADMIN");
        String token = jwt.generateAccountToken(user);
        user.setAccountStatus("LOCKED");
        assertNull(authenticate(token));
        user.setAccountStatus("DISABLED");
        assertNull(authenticate(token));
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.empty());
        assertNull(authenticate(token));
    }

    @Test void malformedTokenDoesNotAuthenticate() throws Exception {
        assertNull(authenticate("not-a-jwt"));
        verifyNoInteractions(users);
    }
}
