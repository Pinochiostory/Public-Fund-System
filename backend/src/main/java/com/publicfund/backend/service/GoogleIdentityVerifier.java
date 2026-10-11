package com.publicfund.backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class GoogleIdentityVerifier {
    private final String clientId;
    private final GoogleIdTokenVerifier verifier;
    public record Identity(String subject, String email, String name, boolean authoritativeEmail) {}
    public GoogleIdentityVerifier(@Value("${PUBLIC_FUND_GOOGLE_CLIENT_ID:}") String clientId) {
        this.clientId = clientId.trim();
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(this.clientId)).build();
    }
    public String clientId() { return clientId; }
    public Identity verify(String credential, String nonce) {
        if (clientId.isBlank()) throw new IllegalArgumentException("Google sign-in is not configured.");
        if (credential == null || credential.length() > 12000 || nonce == null)
            throw new IllegalArgumentException("Google sign-in could not be verified. Please try again.");
        try {
            var token = verifier.verify(credential);
            if (token == null) throw new IllegalArgumentException("Google sign-in expired or could not be verified. Please try again.");
            var p = token.getPayload();
            if (!nonce.equals(p.get("nonce")) || !Boolean.TRUE.equals(p.getEmailVerified())
                    || p.getSubject() == null || p.getEmail() == null
                    || p.getSubject().length() > 255 || p.getEmail().length() > 255
                    || (p.getAuthorizedParty() != null && !clientId.equals(p.getAuthorizedParty())))
                throw new IllegalArgumentException("Google sign-in could not be verified. Please try again.");
            String email = p.getEmail().toLowerCase(java.util.Locale.ROOT);
            boolean authoritative = email.endsWith("@gmail.com")
                    || (p.getHostedDomain() != null && !p.getHostedDomain().isBlank());
            String name = p.get("name") instanceof String value ? value.trim() : "";
            if (name.isBlank() || name.length() > 255) name = email;
            return new Identity(p.getSubject(), email, name, authoritative);
        } catch (java.io.IOException | java.security.GeneralSecurityException e) {
            throw new IllegalArgumentException("Google could not be reached or verified. Try again or use email and password.");
        }
    }
}
