package com.backlogtracker.notification.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.notification.domain.Notification;
import com.backlogtracker.notification.domain.NotificationStatus;
import com.backlogtracker.notification.domain.NotificationType;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);

    long countByUserIdAndStatus(String userId, NotificationStatus status);

    boolean existsByUserIdAndGroupIdAndTypeAndStatus(
            String userId, String groupId, NotificationType type, NotificationStatus status);

    List<Notification> findByGroupIdAndStatus(String groupId, NotificationStatus status);
}
