package com.publicfund.backend.controller;
import com.publicfund.backend.config.SecurityConfig;
import com.publicfund.backend.entity.User;
import com.publicfund.backend.repository.UserRepository;
import com.publicfund.backend.security.JwtAuthenticationFilter;
import com.publicfund.backend.service.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@WebMvcTest(GoogleAuthController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,GoogleLoginChallenge.class})
class GoogleAuthSecurityTests {
    @Autowired MockMvc mvc;
    @Autowired GoogleLoginChallenge challenges;
    @MockitoBean GoogleIdentityVerifier verifier;
    @MockitoBean GoogleAccountService accounts;
    @MockitoBean JwtService jwt;
    @MockitoBean UserRepository users;
    @Test void challengeUsesHttpOnlySameSiteCookieAndDoesNotCache() throws Exception {
        when(verifier.clientId()).thenReturn("client-id");
        mvc.perform(post("/api/auth/google/challenge")).andExpect(status().isOk())
          .andExpect(header().string("Cache-Control","no-store"))
          .andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("HttpOnly")))
          .andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("SameSite=Strict")));
    }
    @Test void missingCookieRejectsLoginBeforeCallingGoogle() throws Exception {
        String n=challenges.issue();
        mvc.perform(post("/api/auth/google/login").param("credential","fake").param("nonce",n)).andExpect(status().isBadRequest());
        verifyNoInteractions(verifier,accounts);
    }
    @Test void invalidGoogleCredentialCannotCreateSession() throws Exception {
        String n=challenges.issue(); when(verifier.verify("invalid",n)).thenThrow(new IllegalArgumentException("Invalid Google sign-in"));
        mvc.perform(post("/api/auth/google/login").cookie(new Cookie("pf_google_nonce",n)).param("credential","invalid").param("nonce",n))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(accounts);
    }
    @Test void validIdentityUsesExistingSessionShapeAndCannotReplay() throws Exception {
        String n=challenges.issue();
        var identity=new GoogleIdentityVerifier.Identity("sub","user@gmail.com","User",true);
        when(verifier.verify("valid",n)).thenReturn(identity);
        User user=new User("User",identity.email(),"hash","SERVICE_PROVIDER"); user.setId(5L);
        when(accounts.login(identity,null)).thenReturn(new AuthService.LoginResult(user,"existing-jwt"));
        for(int i=0;i<2;i++) {
            var result=mvc.perform(post("/api/auth/google/login").cookie(new Cookie("pf_google_nonce",n)).param("credential","valid").param("nonce",n));
            if(i==0)result.andExpect(status().isOk()).andExpect(jsonPath("$.token").value("existing-jwt"))
                    .andExpect(jsonPath("$.user.password").doesNotExist()).andExpect(jsonPath("$.user.id").value(5));
            else result.andExpect(status().isBadRequest());
        }
        verify(accounts,times(1)).login(identity,null);
    }
    @Test void linkConfirmationDoesNotReturnSessionAndKeepsBrowserChallenge() throws Exception {
        String n=challenges.issue(); var identity=new GoogleIdentityVerifier.Identity("sub","user@gmail.com","User",true);
        when(verifier.verify("valid",n)).thenReturn(identity);
        when(accounts.login(identity,null)).thenThrow(new GoogleAccountService.LinkRequired());
        mvc.perform(post("/api/auth/google/login").cookie(new Cookie("pf_google_nonce",n)).param("credential","valid").param("nonce",n))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GOOGLE_LINK_REQUIRED"))
            .andExpect(jsonPath("$.token").doesNotExist());
        assertDoesNotThrow(()->challenges.validate(n,n));
    }
    @Test void untrustedOriginIsRejected() throws Exception {
        mvc.perform(post("/api/auth/google/challenge").header("Origin","https://attacker.invalid")).andExpect(status().isForbidden());
    }
}

