package com.publicfund.backend.service;

import com.publicfund.backend.entity.PasswordResetToken;
import com.publicfund.backend.entity.User;
import com.publicfund.backend.repository.PasswordResetTokenRepository;
import com.publicfund.backend.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffPasswordResetTests {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AuthService auth = new AuthService(users, encoder, mock(JwtService.class));
    private final EmailDeliveryService mail = mock(EmailDeliveryService.class);
    private final PasswordResetService reset = new PasswordResetService(tokens, users, encoder, auth, audit, mail, "http://localhost:5173");
    @org.junit.jupiter.api.BeforeEach void configureMail() { when(mail.isConfigured()).thenReturn(true); }

    @Test void emailEndpointDoesNotDiscloseStaffRecoveryTokens() {
        for (String role : new String[]{"ADMIN", "GOVERNMENT_OFFICER", "ADMINISTRATOR", "VERIFICATION_OFFICER"}) {
            when(users.findByEmailForUpdate("staff@example.com")).thenReturn(Optional.of(new User("Staff", "staff@example.com", "hash", role)));
            assertNull(reset.requestPasswordReset("staff@example.com"));
        }
        verifyNoInteractions(tokens, audit);
    }

    @Test void oldStaffResetTokensCannotBeUsed() {
        User staff = new User("Staff", "staff@example.com", "unchanged-hash", "ADMIN");
        staff.setId(42L);
        when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(staff));
        when(tokens.findUserIdByToken(PasswordResetService.tokenHash("old-token"))).thenReturn(Optional.of(42L));
        when(tokens.findByToken(PasswordResetService.tokenHash("old-token"))).thenReturn(Optional.of(
                new PasswordResetToken(42L, "old-token", LocalDateTime.now().plusMinutes(10))));
        assertThrows(RuntimeException.class, () -> reset.resetPassword("old-token", "Test-Only@123"));
        assertEquals("unchanged-hash", staff.getPassword());
        verify(users, never()).save(any());
        verify(tokens, never()).save(any());
    }

    @Test void providerResetEmailsTokenAndUpdatesHashedPassword() {
        User provider = new User("Provider", "provider@example.com", "old-hash", "SERVICE_PROVIDER");
        provider.setId(42L);
        provider.setAccountStatus("LOCKED");
        provider.setFailedLoginAttempts(3);
        when(users.findByEmailForUpdate(provider.getEmail())).thenReturn(Optional.of(provider));
        when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(provider));
        when(tokens.save(any())).thenAnswer(call -> {
            PasswordResetToken saved = call.getArgument(0);
            when(tokens.findByToken(saved.getToken())).thenReturn(Optional.of(saved));
            when(tokens.findUserIdByToken(saved.getToken())).thenReturn(Optional.of(42L));
            return saved;
        });
        assertNull(reset.requestPasswordReset(provider.getEmail()));
        var body=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq(provider.getEmail()), anyString(), body.capture());
        String token=body.getValue().split("#token=")[1].split("\\s")[0];
        assertFalse(body.getValue().contains("h:"));
        verify(tokens).deleteByUserId(42L);
        reset.resetPassword(token, "Test-Only@123");
        assertTrue(encoder.matches("Test-Only@123", provider.getPassword()));
        assertEquals("ACTIVE", provider.getAccountStatus());
        assertEquals(0, provider.getFailedLoginAttempts());
    }
}
