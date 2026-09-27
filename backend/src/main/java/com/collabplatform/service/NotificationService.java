package com.collabplatform.service;

import com.collabplatform.dto.notification.NotificationResponse;
import com.collabplatform.entity.Notification;
import com.collabplatform.entity.Project;
import com.collabplatform.entity.Task;
import com.collabplatform.entity.User;
import com.collabplatform.entity.enums.NotificationType;
import com.collabplatform.exception.ForbiddenException;
import com.collabplatform.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final WebSocketEventPublisher eventPublisher;

    @Transactional
    public void notify(User recipient, Project project, Task task, NotificationType type, String message) {
        Notification notification = Notification.builder()
                .recipient(recipient)
                .project(project)
                .task(task)
                .type(type)
                .message(message)
                .build();
        notification = notificationRepository.save(notification);

        eventPublisher.publishNotification(recipient.getId(), toResponse(notification));
    }

    public List<NotificationResponse> listForUser(Long userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ForbiddenException("Notification not found"));
        if (!notification.getRecipient().getId().equals(userId)) {
            throw new ForbiddenException("You cannot modify another user's notifications");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getProject().getId(),
                n.getTask() != null ? n.getTask().getId() : null,
                n.getType(),
                n.getMessage(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
