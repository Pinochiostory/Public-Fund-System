package com.publicfund.backend.service;

import com.publicfund.backend.entity.*;
import com.publicfund.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class EmailVerificationService {
    private final UserRepository users;
    private final EmailVerificationRepository codes;
    private final PasswordEncoder encoder;
    private final EmailDeliveryService mail;
    private final AuditLogService audit;
    private final SecureRandom random=new SecureRandom();
    public EmailVerificationService(UserRepository users, EmailVerificationRepository codes,
            PasswordEncoder encoder, EmailDeliveryService mail, AuditLogService audit) {
        this.users=users; this.codes=codes; this.encoder=encoder; this.mail=mail; this.audit=audit;
    }
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void sendCode(String email) {
        User user=account(email);
        if (user.isEmailVerified()) throw new IllegalArgumentException("This email is already verified. Please sign in.");
        LocalDateTime now=LocalDateTime.now();
        EmailVerification code=codes.findById(user.getId()).orElseGet(EmailVerification::new);
        code.userId=user.getId();
        if (code.lastSentAt!=null && code.lastSentAt.plusSeconds(60).isAfter(now))
            throw new IllegalArgumentException("Please wait 60 seconds before requesting another code.");
        if (code.windowStartedAt==null || !code.windowStartedAt.plusHours(1).isAfter(now)) {
            code.windowStartedAt=now; code.sendCount=0;
        }
        if (code.sendCount>=3) throw new IllegalArgumentException("Too many verification requests. Please try again in one hour.");
        String otp=String.format("%06d",random.nextInt(1_000_000));
        code.lastSentAt=now; code.sendCount++; code.attempts=0;
        code.codeHash=encoder.encode(otp); code.expiresAt=now.plusMinutes(10);
        codes.save(code);
        try { mail.send(user.getEmail(),"Verify your Public Fund account",
                "Your verification code is " + otp + ". It expires in 10 minutes. If you did not request this, ignore this email."); }
        catch (EmailDeliveryService.DeliveryFailure ex) {
            code.codeHash=null; code.expiresAt=null; codes.save(code);
            audit.logAction(user.getId(),user.getEmail(),user.getRole(),"VERIFICATION_EMAIL_FAILED","Verification email was not delivered. No code recorded in the audit log.");
            throw ex;
        }
    }
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void verify(String email, String value) {
        User user=account(email);
        if (user.isEmailVerified()) throw new IllegalArgumentException("This email is already verified. Please sign in.");
        EmailVerification code=codes.findById(user.getId()).orElseThrow(() -> new IllegalArgumentException("Request a verification code first."));
        if (code.expiresAt==null || !code.expiresAt.isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("This verification code has expired. Request a new code.");
        if (code.attempts>=5) throw new IllegalArgumentException("Too many incorrect codes. Request a new code.");
        code.attempts++; codes.save(code);
        if (value==null || !value.matches("[0-9]{6}") || code.codeHash==null || !encoder.matches(value,code.codeHash))
            throw new IllegalArgumentException("The verification code is incorrect.");
        user.setEmailVerified(true); users.save(user);
        code.codeHash=null; code.expiresAt=null; codes.save(code);
        audit.logAction(user.getId(),user.getEmail(),user.getRole(),"EMAIL_VERIFIED","Service Provider verified their email address.");
    }
    private User account(String email) {
        if (email==null || email.isBlank()) throw new IllegalArgumentException("Email is required.");
        User user=users.findByEmailForUpdate(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("No pending registration was found for this email."));
        if (!"SERVICE_PROVIDER".equals(user.getRole()) || !"ACTIVE".equals(user.getAccountStatus()))
            throw new IllegalArgumentException("This account cannot use public email verification. Contact the system owner.");
        return user;
    }
}
