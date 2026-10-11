package com.publicfund.backend.service;
import com.publicfund.backend.entity.User;
import com.publicfund.backend.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;

class GoogleAccountSafetyTests {
    final UserRepository users=mock(UserRepository.class);
    final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(4);
    final JwtService jwt=mock(JwtService.class);
    final AuditLogService audit=mock(AuditLogService.class);
    final AuthService auth=new AuthService(users,encoder,jwt);
    final GoogleAccountService service=new GoogleAccountService(users,encoder,auth,jwt,audit);
    final GoogleIdentityVerifier.Identity identity=new GoogleIdentityVerifier.Identity("google-id","provider@gmail.com","Provider",true);
    User user;
    @BeforeEach void setup() {
        user=new User("Provider","provider@gmail.com",encoder.encode("Existing@123"),"SERVICE_PROVIDER");
        user.setId(7L);
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(users.saveAndFlush(any())).thenAnswer(call -> { User row=call.getArgument(0); if(row.getId()==null)row.setId(8L); return row; });
        when(jwt.generateAccountToken(any())).thenReturn("app-session");
    }
    @Test void newGoogleUserIsOnlyProviderWithRandomBcryptPassword() {
        when(users.findByEmailForUpdate(identity.email())).thenReturn(Optional.empty());
        User created=service.login(identity,null).getUser();
        assertEquals("SERVICE_PROVIDER",created.getRole()); assertTrue(created.isEmailVerified());
        assertEquals("google-id",created.getGoogleSubject()); assertTrue(created.getPassword().startsWith("$2"));
        assertFalse(encoder.matches("Existing@123",created.getPassword()));
    }
    @Test void existingEmailIsNotAutomaticallyLinked() {
        assertThrows(GoogleAccountService.LinkRequired.class,()->service.login(identity,null));
        assertNull(user.getGoogleSubject()); verifyNoInteractions(jwt);
    }
    @Test void matchingPasswordLinksExistingUserWithoutReplacingProjectsOrPassword() {
        String old=user.getPassword();
        assertSame(user,service.login(identity,"Existing@123").getUser());
        assertEquals(old,user.getPassword()); assertEquals(7L,user.getId()); assertEquals("google-id",user.getGoogleSubject());
    }
    @Test void wrongPasswordNeverLinksAndPreservesLockoutRules() {
        for(int i=0;i<3;i++)assertThrows(IllegalArgumentException.class,()->service.login(identity,"wrong"));
        assertNull(user.getGoogleSubject()); assertEquals("LOCKED",user.getAccountStatus()); verifyNoInteractions(jwt);
    }
    @Test void staffCannotUseGoogleToGainOrBypassPrivilegedAccess() {
        for(String role:new String[]{"ADMIN","ADMINISTRATOR","GOVERNMENT_OFFICER","VERIFICATION_OFFICER"}) {
            user.setRole(role); assertThrows(IllegalArgumentException.class,()->service.login(identity,"Existing@123"));
        }
        verifyNoInteractions(jwt);
    }
    @Test void disabledLockedUnverifiedAndTemporaryPasswordUsersCannotBypassRestrictions() {
        user.setAccountStatus("DISABLED"); assertThrows(IllegalArgumentException.class,()->service.login(identity,null));
        user.setAccountStatus("LOCKED"); assertThrows(IllegalArgumentException.class,()->service.login(identity,null));
        user.setAccountStatus("ACTIVE"); user.setEmailVerified(false); assertThrows(IllegalArgumentException.class,()->service.login(identity,null));
        user.setEmailVerified(true); user.setMustChangePassword(true); assertThrows(IllegalArgumentException.class,()->service.login(identity,null));
        verifyNoInteractions(jwt);
    }
    @Test void subsequentLoginUsesStableGoogleSubjectNotEmailToIdentifyAccount() {
        user.setGoogleSubject(identity.subject());
        when(users.findByGoogleSubject(identity.subject())).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        assertSame(user,service.login(new GoogleIdentityVerifier.Identity(identity.subject(),"renamed@gmail.com","Name",true),null).getUser());
        verify(users,never()).findByEmailForUpdate("renamed@gmail.com");
    }
    @Test void cannotReplaceAnotherGoogleIdentity() {
        user.setGoogleSubject("another-id");
        assertThrows(IllegalArgumentException.class,()->service.login(identity,"Existing@123"));
        assertEquals("another-id",user.getGoogleSubject());
    }
    @Test void thirdPartyGoogleEmailMustBeVerifiedLocallyFirst() {
        when(users.findByEmailForUpdate(identity.email())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,()->service.login(
            new GoogleIdentityVerifier.Identity("id",identity.email(),"Provider",false),null));
        verify(users,never()).saveAndFlush(any());
    }
    @Test void challengesRejectWrongBrowserAndReplay() {
        GoogleLoginChallenge challenges=new GoogleLoginChallenge();
        String nonce=challenges.issue();
        assertThrows(IllegalArgumentException.class,()->challenges.validate(nonce,"another-browser"));
        challenges.consume(nonce,nonce);
        assertThrows(IllegalArgumentException.class,()->challenges.validate(nonce,nonce));
        assertThrows(IllegalArgumentException.class,()->challenges.validate(null,null));
    }
    @Test void unconfiguredGoogleFailsClosed() {
        assertThrows(IllegalArgumentException.class,()->new GoogleIdentityVerifier("").verify("anything","nonce"));
    }
}

