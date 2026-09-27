package com.collabplatform.dto.notification;

import com.collabplatform.entity.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private Long projectId;
    private Long taskId;
    private NotificationType type;
    private String message;
    private boolean read;
    private Instant createdAt;
}
