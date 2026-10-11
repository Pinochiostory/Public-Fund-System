package com.publicfund.backend.service;

import com.publicfund.backend.entity.PasswordResetToken;
import com.publicfund.backend.entity.User;
import com.publicfund.backend.repository.PasswordResetTokenRepository;
import com.publicfund.backend.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailPasswordResetSafetyTests {
    final UserRepository users=mock(UserRepository.class);
    final PasswordResetTokenRepository tokens=mock(PasswordResetTokenRepository.class);
    final EmailDeliveryService mail=mock(EmailDeliveryService.class);
    final AuditLogService audit=mock(AuditLogService.class);
    final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(4);
    final AuthService auth=new AuthService(users,encoder,mock(JwtService.class));
    final PasswordResetService reset=new PasswordResetService(tokens,users,encoder,auth,audit,mail,"http://localhost:5173");
    User provider() {
        User u=new User("Provider","provider@example.test","unchanged-hash","SERVICE_PROVIDER");
        u.setId(7L);
        when(mail.isConfigured()).thenReturn(true);
        when(users.findByEmailForUpdate(u.getEmail())).thenReturn(Optional.of(u));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(u));
        return u;
    }
    PasswordResetToken stored(String raw) {
        var token=new PasswordResetToken(7L,PasswordResetService.tokenHash(raw),LocalDateTime.now().plusMinutes(30));
        when(tokens.findUserIdByToken(token.getToken())).thenReturn(Optional.of(7L));
        when(tokens.findByToken(token.getToken())).thenReturn(Optional.of(token));
        return token;
    }
    @Test void onlyHashIsStoredAndNoSecretIsReturnedOrAudited() {
        var u=provider();
        assertNull(reset.requestPasswordReset(u.getEmail()));
        var body=ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq(u.getEmail()),anyString(),body.capture());
        String raw=body.getValue().split("#token=")[1].split("\\s")[0];
        assertEquals(43,raw.length());
        var saved=ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());
        assertEquals(PasswordResetService.tokenHash(raw),saved.getValue().getToken());
        assertNotEquals(raw,saved.getValue().getToken());
        verify(audit).logAction(7L,u.getEmail(),u.getRole(),"PASSWORD_RESET_REQUESTED","Password reset requested");
    }
    @Test void missingMailConfigurationFailsBeforeWritingAnything() {
        assertThrows(IllegalArgumentException.class,()->reset.requestPasswordReset("provider@example.test"));
        verifyNoInteractions(users,tokens,audit);
    }
    @Test void deliveryFailurePropagatesRatherThanReportingSuccess() {
        var u=provider();
        doThrow(new EmailDeliveryService.DeliveryFailure("Email could not be sent.")).when(mail).send(anyString(),anyString(),anyString());
        assertThrows(EmailDeliveryService.DeliveryFailure.class,()->reset.requestPasswordReset(u.getEmail()));
    }
    @Test void unknownOrDisabledAccountDoesNotSendOrReturnCredential() {
        var u=provider();u.setAccountStatus("DISABLED");
        assertNull(reset.requestPasswordReset(u.getEmail()));
        assertNull(reset.requestPasswordReset("unknown@example.test"));
        verify(mail,never()).send(anyString(),anyString(),anyString());
        verifyNoInteractions(tokens,audit);
    }
    @Test void expiredAndUsedLinksCannotChangePassword() {
        var u=provider();var token=stored("test-only-link");
        token.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThrows(IllegalArgumentException.class,()->reset.resetPassword("test-only-link","Test-Only@123"));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(1));token.setUsed(true);
        assertThrows(IllegalArgumentException.class,()->reset.resetPassword("test-only-link","Test-Only@123"));
        assertEquals("unchanged-hash",u.getPassword());verify(users,never()).save(any());
    }
    @Test void successfulLinkIsSingleUseAndPasswordIsBcrypt() {
        var u=provider();var token=stored("test-only-link");
        reset.resetPassword("test-only-link","Test-Only@123");
        assertTrue(encoder.matches("Test-Only@123",u.getPassword()));assertTrue(token.isUsed());
        assertThrows(IllegalArgumentException.class,()->reset.resetPassword("test-only-link","Different@123"));
        assertTrue(encoder.matches("Test-Only@123",u.getPassword()));
        verify(users,times(1)).save(u);
    }
    @Test void oldPlaintextDevelopmentTokenCannotBeUsed() {
        provider();
        when(tokens.findByToken("old-development-token")).thenReturn(Optional.of(new PasswordResetToken(7L,"old-development-token",LocalDateTime.now().plusMinutes(10))));
        assertThrows(IllegalArgumentException.class,()->reset.resetPassword("old-development-token","Test-Only@123"));
        verify(tokens,never()).findByToken("old-development-token");verify(users,never()).save(any());
    }
    @Test void externalHttpResetOriginIsRejected() {
        assertThrows(IllegalArgumentException.class,()->new PasswordResetService(tokens,users,encoder,auth,audit,mail,"http://public.example.test"));
    }
}
