package com.publicfund.backend.controller;

import com.publicfund.backend.entity.Notification;
import com.publicfund.backend.repository.UserRepository;
import com.publicfund.backend.service.NotificationService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "http://localhost:5173")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(
            NotificationService notificationService,
            UserRepository userRepository) {

        this.notificationService =
                notificationService;

        this.userRepository =
                userRepository;
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyNotifications(
            Authentication authentication) {

        try {

            Long userId =
                    getUserId(authentication);

            return ResponseEntity.ok(
                    notificationService
                            .getUserNotifications(
                                    userId
                            )
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    @GetMapping("/my/unread")
    public ResponseEntity<?> getUnreadNotifications(
            Authentication authentication) {

        try {

            Long userId =
                    getUserId(authentication);

            return ResponseEntity.ok(
                    notificationService
                            .getUnreadNotifications(
                                    userId
                            )
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    @GetMapping("/my/unread-count")
    public ResponseEntity<?> getUnreadCount(
            Authentication authentication) {

        try {

            Long userId =
                    getUserId(authentication);

            long unreadCount =
                    notificationService
                            .getUnreadCount(
                                    userId
                            );

            return ResponseEntity.ok(
                    new UnreadCountResponse(
                            unreadCount
                    )
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<?> markAsRead(
            @PathVariable Long notificationId,
            Authentication authentication) {

        try {

            Long userId =
                    getUserId(authentication);

            Notification notification =
                    notificationService
                            .markAsRead(
                                    notificationId,
                                    userId
                            );

            return ResponseEntity.ok(
                    notification
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    @PostMapping("/my/read-all")
    public ResponseEntity<?> markAllAsRead(
            Authentication authentication) {

        try {

            Long userId =
                    getUserId(authentication);

            notificationService
                    .markAllAsRead(
                            userId
                    );

            return ResponseEntity.ok(
                    "All notifications marked as read"
            );

        } catch (RuntimeException e) {

            return ApiErrors.response(e);
        }
    }

    private Long getUserId(
            Authentication authentication) {

        if (authentication == null) {

            throw new RuntimeException(
                    "Authentication is required"
            );
        }

        String email =
                authentication.getName();

        return userRepository
                .findByEmail(email)
                .map(user -> user.getId())
                .orElseThrow(() ->
                        new RuntimeException(
                                "User account not found"
                        ));
    }

    public static class UnreadCountResponse {

        private final long unreadCount;

        public UnreadCountResponse(
                long unreadCount) {

            this.unreadCount =
                    unreadCount;
        }

        public long getUnreadCount() {
            return unreadCount;
        }
    }
}
