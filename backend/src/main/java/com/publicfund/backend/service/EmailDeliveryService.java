package com.publicfund.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/** SMTP credentials come only from configuration. Message bodies and credentials are never logged. */
@Service
public class EmailDeliveryService {
    private final JavaMailSenderImpl sender = new JavaMailSenderImpl();
    private final String from;
    private final boolean configured;
    public EmailDeliveryService(@Value("${PUBLIC_FUND_MAIL_HOST:}") String host,
            @Value("${PUBLIC_FUND_MAIL_PORT:587}") int port,
            @Value("${PUBLIC_FUND_MAIL_USERNAME:}") String username,
            @Value("${PUBLIC_FUND_MAIL_PASSWORD:}") String password,
            @Value("${PUBLIC_FUND_MAIL_FROM:}") String from,
            @Value("${PUBLIC_FUND_MAIL_TLS:true}") boolean tls) {
        this.from=from; configured=!host.isBlank() && !from.isBlank() && (username.isBlank() || !password.isBlank());
        sender.setHost(host); sender.setPort(port); sender.setUsername(username); sender.setPassword(password);
        var p=sender.getJavaMailProperties();
        p.setProperty("mail.smtp.auth", Boolean.toString(!username.isBlank()));
        p.setProperty("mail.smtp.starttls.enable", Boolean.toString(tls));
        p.setProperty("mail.smtp.starttls.required", Boolean.toString(tls));
        p.setProperty("mail.smtp.connectiontimeout","5000");
        p.setProperty("mail.smtp.timeout","5000");
        p.setProperty("mail.smtp.writetimeout","5000");
    }
    public boolean isConfigured() { return configured; }
    public void send(String email, String subject, String body) {
        if (!configured) throw new DeliveryFailure("Email delivery is not configured. Ask the system owner to configure email, then request a new code.");
        try {
            SimpleMailMessage message=new SimpleMailMessage();
            message.setFrom(from); message.setTo(email); message.setSubject(subject); message.setText(body);
            sender.send(message);
        } catch (org.springframework.mail.MailException ex) {
            throw new DeliveryFailure("The email could not be sent. Please try requesting a new email later.");
        }
    }
    public static class DeliveryFailure extends IllegalArgumentException {
        public DeliveryFailure(String message) { super(message); }
    }
}
