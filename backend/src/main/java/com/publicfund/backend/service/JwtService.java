package com.publicfund.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Base64;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.publicfund.backend.entity.User;
import com.publicfund.backend.security.AccountRoles;

@Service
public class JwtService {

    private final String secretKey;

    public JwtService(@org.springframework.beans.factory.annotation.Value("${app.jwt.secret}") String secretKey) {
        if (secretKey == null || secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("PUBLIC_FUND_JWT_SECRET must contain at least 32 bytes.");
        }
        this.secretKey = secretKey;
    }

    private static final long EXPIRATION_TIME =
            1000 * 60 * 60 * 24;

    private SecretKey getSigningKey() {

        return Keys.hmacShaKeyFor(
                secretKey.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    public String generateToken(
            Long userId,
            String email,
            String role) {

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + EXPIRATION_TIME
                        )
                )
                .signWith(getSigningKey())
                .compact();
    }

    public Claims extractClaims(String token) {

        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String generateAccountToken(User user) {
        return Jwts.builder().subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", AccountRoles.loginRole(user.getRole()))
                .claim("credentialStamp", credentialStamp(user))
                .claim("mustChangePassword", user.isMustChangePassword())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() +
                        (user.isMustChangePassword() ? 15 * 60 * 1000L : EXPIRATION_TIME)))
                .signWith(getSigningKey()).compact();
    }

    public boolean isCurrentAccountToken(Claims claims, User user) {
        String stamp = claims.get("credentialStamp", String.class);
        return stamp != null && user.getPassword() != null
                && Boolean.valueOf(user.isMustChangePassword()).equals(claims.get("mustChangePassword", Boolean.class))
                && MessageDigest.isEqual(stamp.getBytes(StandardCharsets.UTF_8),
                        credentialStamp(user).getBytes(StandardCharsets.UTF_8));
    }

    // A keyed fingerprint invalidates sessions after a password change without another
    // users-table column. Neither plaintext passwords nor BCrypt hashes enter the JWT.
    private String credentialStamp(User user) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal((user.getId() + ":" + user.getPassword()).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Unable to create account session", e);
        }
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractClaims(token)
                .get("role", String.class);
    }

    public boolean isTokenValid(String token) {

        try {
            extractClaims(token);
            return true;

        } catch (Exception e) {
            return false;
        }
    }
}
