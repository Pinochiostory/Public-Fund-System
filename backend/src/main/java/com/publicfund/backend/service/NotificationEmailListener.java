package com.publicfund.backend.service;

import com.publicfund.backend.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Component
public class NotificationEmailListener {
    private final EmailDeliveryService mail;
    private final UserRepository users;
    private final AuditLogService audit;
    public NotificationEmailListener(EmailDeliveryService mail, UserRepository users, AuditLogService audit) {
        this.mail=mail;this.users=users;this.audit=audit;
    }
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(NotificationService.NotificationCreated event) {
        if (!mail.isConfigured()) return;
        var user=users.findById(event.userId()).orElse(null);
        if(user==null) return;
        try { mail.send(user.getEmail(),event.title(),event.message()); }
        catch (EmailDeliveryService.DeliveryFailure ex) {
            audit.logAction(null,"SYSTEM","SYSTEM","NOTIFICATION_EMAIL_FAILED",
                    "In-app notification " + event.id() + " for user " + event.userId() +
                    " is saved, but its email was not delivered. The underlying action remains recorded.");
        }
    }
}
