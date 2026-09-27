package com.collabplatform.dto.activity;

import com.collabplatform.entity.enums.ActivityType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class ActivityResponse {
    private Long id;
    private Long projectId;
    private Long taskId;
    private String actorName;
    private ActivityType type;
    private String message;
    private Instant createdAt;
}
