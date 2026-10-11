package com.publicfund.backend.controller;

import com.publicfund.backend.dto.AuthResponse;
import com.publicfund.backend.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/google")
public class GoogleAuthController {
    private final GoogleIdentityVerifier verifier;
    private final GoogleLoginChallenge challenges;
    private final GoogleAccountService accounts;
    public GoogleAuthController(GoogleIdentityVerifier verifier, GoogleLoginChallenge challenges, GoogleAccountService accounts) {
        this.verifier=verifier; this.challenges=challenges; this.accounts=accounts;
    }
    @GetMapping("/config")
    public ResponseEntity<?> config() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("clientId",verifier.clientId()));
    }
    @PostMapping("/challenge")
    public ResponseEntity<?> challenge(HttpServletRequest request) {
        if (verifier.clientId().isBlank()) throw new IllegalArgumentException("Google sign-in is not configured.");
        String nonce=challenges.issue();
        var cookie=ResponseCookie.from("pf_google_nonce",nonce).httpOnly(true).secure(request.isSecure())
                .sameSite("Strict").path("/api/auth/google").maxAge(300).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.SET_COOKIE,cookie.toString())
                .body(Map.of("nonce",nonce));
    }
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestParam String credential, @RequestParam String nonce,
            @RequestParam(required=false) String existingPassword,
            @CookieValue(name="pf_google_nonce",required=false) String cookie) {
        challenges.validate(nonce,cookie);
        var identity=verifier.verify(credential,nonce);
        // Keep a challenge only for the explicit password-confirmation step.
        try {
            synchronized (challenges) {
                challenges.validate(nonce,cookie);
                var result=accounts.login(identity,existingPassword);
                challenges.consume(nonce,cookie);
                var u=result.getUser();
                return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new AuthController.LoginResponse(
                        new AuthResponse(u.getId(),u.getFullName(),u.getEmail(),u.getRole(),u.getAccountStatus(),u.isMustChangePassword()), result.getToken()));
            }
        } catch (GoogleAccountService.LinkRequired e) {
            return ResponseEntity.status(409).cacheControl(CacheControl.noStore()).body(Map.of("code","GOOGLE_LINK_REQUIRED","message",e.getMessage()));
        } catch (RuntimeException e) {
            challenges.consume(nonce,cookie);
            throw e;
        }
    }
}
