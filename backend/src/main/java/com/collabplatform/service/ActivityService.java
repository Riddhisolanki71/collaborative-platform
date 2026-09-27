package com.collabplatform.service;

import com.collabplatform.dto.activity.ActivityResponse;
import com.collabplatform.dto.ws.WsEventType;
import com.collabplatform.entity.Activity;
import com.collabplatform.entity.Project;
import com.collabplatform.entity.Task;
import com.collabplatform.entity.User;
import com.collabplatform.entity.enums.ActivityType;
import com.collabplatform.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Records project activity (audit trail) and broadcasts it in real time.
 * This is intentionally called from within the same transaction as the
 * triggering action (e.g. task status change) so the activity row and the
 * underlying change are committed atomically.
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final WebSocketEventPublisher eventPublisher;

    @Transactional
    public void record(Project project, User actor, Task task, ActivityType type, String message) {
        Activity activity = Activity.builder()
                .project(project)
                .actor(actor)
                .task(task)
                .type(type)
                .message(message)
                .build();
        activity = activityRepository.save(activity);

        eventPublisher.publishToProject(project.getId(), WsEventType.ACTIVITY_CREATED, toResponse(activity));
    }

    public List<ActivityResponse> listForProject(Long projectId) {
        return activityRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    private ActivityResponse toResponse(Activity a) {
        return new ActivityResponse(
                a.getId(),
                a.getProject().getId(),
                a.getTask() != null ? a.getTask().getId() : null,
                a.getActor().getName(),
                a.getType(),
                a.getMessage(),
                a.getCreatedAt()
        );
    }
}
