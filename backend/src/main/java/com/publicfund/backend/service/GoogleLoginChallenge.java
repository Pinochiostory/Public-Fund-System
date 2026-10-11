package com.publicfund.backend.service;

import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** One-use login nonces bound to an HttpOnly browser cookie. No credentials are cached. */
@Service
public class GoogleLoginChallenge {
    private final Map<String, Instant> pending = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    public synchronized String issue() {
        pending.values().removeIf(expiry -> !expiry.isAfter(Instant.now()));
        if (pending.size() >= 10000) throw new IllegalArgumentException("Google sign-in is busy. Please try again shortly.");
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pending.put(nonce, Instant.now().plusSeconds(300));
        return nonce;
    }
    public synchronized void validate(String nonce, String cookie) {
        if (nonce == null || !nonce.equals(cookie) || !pending.getOrDefault(nonce, Instant.MIN).isAfter(Instant.now()))
            throw new IllegalArgumentException("Google sign-in expired. Please start again.");
    }
    public synchronized void consume(String nonce, String cookie) {
        validate(nonce, cookie);
        pending.remove(nonce);
    }
}
