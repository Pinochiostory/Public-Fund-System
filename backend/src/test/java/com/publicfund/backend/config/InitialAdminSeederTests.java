package com.publicfund.backend.config;

import com.publicfund.backend.entity.User;
import com.publicfund.backend.repository.UserRepository;
import com.publicfund.backend.service.AuthService;
import com.publicfund.backend.service.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InitialAdminSeederTests {
    private final UserRepository users = mock(UserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AuthService auth = new AuthService(users, encoder, mock(JwtService.class));

    private void seed(String name, String email, String password) {
        new InitialAdminSeeder(users, auth, name, email, password).run(null);
    }

    @Test void absentConfigurationDoesNotCreateDefaultAccounts() {
        seed("", "", "");
        verifyNoInteractions(users);
    }

    @Test void partialConfigurationFailsWithoutDatabaseWrites() {
        assertThrows(IllegalStateException.class, () -> seed("Name", "", ""));
        assertThrows(IllegalStateException.class, () -> seed("Name", "a@example.com", ""));
        assertThrows(IllegalStateException.class, () -> seed("", "a@example.com", "Test-Only@123"));
        verifyNoInteractions(users);
    }

    @Test void createsHashedAdminOnceAndNeverOverwritesOnRestart() {
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(71L);
            when(users.findByEmail(saved.getEmail())).thenReturn(Optional.of(saved));
            return saved;
        });
        seed(" Example Admin ", " ADMIN@EXAMPLE.COM ", "Test-Only@123");
        User saved = users.findByEmail("admin@example.com").orElseThrow();
        assertEquals("ADMIN", saved.getRole());
        assertEquals("Example Admin", saved.getFullName());
        assertTrue(encoder.matches("Test-Only@123", saved.getPassword()));
        String hash = saved.getPassword();
        seed("Different Name", "admin@example.com", "Changed-Only@456");
        verify(users, times(1)).save(any());
        assertEquals(hash, saved.getPassword());
        assertEquals("Example Admin", saved.getFullName());
    }

    @Test void neverElevatesExistingNonAdminAccount() {
        User existing = new User("Provider", "person@example.com", "existing-hash", "SERVICE_PROVIDER");
        when(users.findByEmail(existing.getEmail())).thenReturn(Optional.of(existing));
        assertThrows(IllegalStateException.class, () -> seed("Admin", existing.getEmail(), "Test-Only@123"));
        assertEquals("SERVICE_PROVIDER", existing.getRole());
        assertEquals("existing-hash", existing.getPassword());
        verify(users, never()).save(any());
    }

    @Test void preservesLegacyAdminAndLockedState() {
        User existing = new User("Existing Admin", "admin@example.com", "existing-hash", "ADMINISTRATOR");
        existing.setAccountStatus("LOCKED");
        when(users.findByEmail(existing.getEmail())).thenReturn(Optional.of(existing));
        seed("Admin", existing.getEmail(), "Test-Only@123");
        assertEquals("LOCKED", existing.getAccountStatus());
        verify(users, never()).save(any());
    }

    @Test void invalidBootstrapCredentialsNeverPersist() {
        assertThrows(IllegalArgumentException.class, () -> seed("Admin", "admin@example.com", "weak"));
        verify(users, never()).save(any());
    }
}
