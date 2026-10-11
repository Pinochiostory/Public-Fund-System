package com.publicfund.backend.repository;

import com.publicfund.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(
            Long userId
    );

    List<Notification>
            findByUserIdAndReadStatusOrderByCreatedAtDesc(
                    Long userId,
                    boolean readStatus
            );

    long countByUserIdAndReadStatus(
            Long userId,
            boolean readStatus
    );
}