package com.backlogtracker.commons.notification.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.backlogtracker.commons.notification.domain.Notification;
import com.backlogtracker.commons.notification.domain.NotificationStatus;
import com.backlogtracker.commons.notification.domain.NotificationType;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);

    long countByUserIdAndStatus(String userId, NotificationStatus status);

    long countByUserIdAndStatusAndActionableTrue(String userId, NotificationStatus status);

    List<Notification> findByArchiveRequestId(String archiveRequestId);

    List<Notification> findByTypeAndReferenceIdAndStatus(
            NotificationType type, String referenceId, NotificationStatus status);

    boolean existsByUserIdAndGroupIdAndTypeAndStatus(
            String userId, String groupId, NotificationType type, NotificationStatus status);

    List<Notification> findByGroupIdAndStatus(String groupId, NotificationStatus status);

    List<Notification> findByGroupIdAndTypeAndStatusOrderByCreatedAtDesc(
            String groupId, NotificationType type, NotificationStatus status);
}
