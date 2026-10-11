package com.publicfund.backend.service;

import com.publicfund.backend.entity.Notification;
import com.publicfund.backend.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@org.springframework.transaction.annotation.Transactional
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final com.publicfund.backend.repository.UserRepository users;

    public NotificationService(org.springframework.context.ApplicationEventPublisher events, com.publicfund.backend.repository.UserRepository users, 
            NotificationRepository notificationRepository) {

        this.events=events; this.users=users;
        this.notificationRepository =
                notificationRepository;
    }

    public Notification createNotification(
            Long userId,
            String title,
            String message) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "Notification user ID is required"
            );
        }

        Notification notification =
                new Notification(
                        userId,
                        title,
                        message
                );

        Notification saved=notificationRepository.save(notification);
        events.publishEvent(new NotificationCreated(saved.getId(), userId, title, message));
        return saved;
    }


    public record NotificationCreated(Long id, Long userId, String title, String message) {}

    public void alertAdministrators(String title, String message) {
        users.findAll().stream().filter(u -> com.publicfund.backend.security.AccountRoles.isAdmin(u.getRole())
                && "ACTIVE".equals(u.getAccountStatus())).forEach(u -> {
            boolean exists=notificationRepository.findByUserIdAndReadStatusOrderByCreatedAtDesc(u.getId(),false)
                    .stream().anyMatch(n -> title.equals(n.getTitle()) && message.equals(n.getMessage()));
            if (!exists) createNotification(u.getId(),title,message);
        });
    }


    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void alertAfterRejectedAction(String title, String message) {
        alertAdministrators(title,message);
    }

    public List<Notification> getUserNotifications(
            Long userId) {

        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(
                        userId
                );
    }

    public List<Notification> getUnreadNotifications(
            Long userId) {

        return notificationRepository
                .findByUserIdAndReadStatusOrderByCreatedAtDesc(
                        userId,
                        false
                );
    }

    public long getUnreadCount(Long userId) {

        return notificationRepository
                .countByUserIdAndReadStatus(
                        userId,
                        false
                );
    }

    public Notification markAsRead(
            Long notificationId,
            Long userId) {

        Notification notification =
                notificationRepository
                        .findById(notificationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Notification not found"
                                ));

        if (!notification.getUserId()
                .equals(userId)) {

            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not have permission to perform this action."
            );
        }

        notification.setReadStatus(true);

        return notificationRepository.save(
                notification
        );
    }

    public void markAllAsRead(Long userId) {

        List<Notification> notifications =
                notificationRepository
                        .findByUserIdAndReadStatusOrderByCreatedAtDesc(
                                userId,
                                false
                        );

        for (Notification notification : notifications) {

            notification.setReadStatus(true);
        }

        notificationRepository.saveAll(
                notifications
        );
    }
}
