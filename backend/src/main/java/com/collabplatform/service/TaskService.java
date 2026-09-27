package com.collabplatform.service;

import com.collabplatform.dto.task.TaskAssignRequest;
import com.collabplatform.dto.task.TaskRequest;
import com.collabplatform.dto.task.TaskResponse;
import com.collabplatform.dto.task.TaskStatusUpdateRequest;
import com.collabplatform.dto.task.TaskUpdateRequest;
import com.collabplatform.dto.user.UserResponse;
import com.collabplatform.dto.ws.WsEventType;
import com.collabplatform.entity.Project;
import com.collabplatform.entity.ProjectMember;
import com.collabplatform.entity.Task;
import com.collabplatform.entity.User;
import com.collabplatform.entity.enums.ActivityType;
import com.collabplatform.entity.enums.NotificationType;
import com.collabplatform.entity.enums.TaskStatus;
import com.collabplatform.exception.BadRequestException;
import com.collabplatform.exception.ConflictException;
import com.collabplatform.exception.ForbiddenException;
import com.collabplatform.exception.ResourceNotFoundException;
import com.collabplatform.repository.ProjectMemberRepository;
import com.collabplatform.repository.TaskRepository;
import com.collabplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ProjectService projectService;
    private final ActivityService activityService;
    private final NotificationService notificationService;
    private final WebSocketEventPublisher eventPublisher;

    @Transactional
    public TaskResponse createTask(Long projectId, TaskRequest request, Long creatorId) {
        Project project = projectService.findProjectOrThrow(projectId);
        ProjectMember creatorMembership = projectService.requireMembership(projectId, creatorId);
        User creator = creatorMembership.getUser();

        User assignee = null;
        if (request.getAssigneeId() != null) {
            assignee = resolveAndValidateAssignee(projectId, request.getAssigneeId());
        }

        Task task = Task.builder()
                .project(project)
                .title(request.getTitle())
                .description(request.getDescription())
                .status(TaskStatus.TODO)
                .priority(request.getPriority() != null ? request.getPriority() : com.collabplatform.entity.enums.Priority.MEDIUM)
                .assignee(assignee)
                .creator(creator)
                .dueDate(request.getDueDate())
                .build();
        task = taskRepository.save(task);

        activityService.record(project, creator, task, ActivityType.TASK_CREATED,
                creator.getName() + " created task \"" + task.getTitle() + "\".");

        if (assignee != null && !assignee.getId().equals(creator.getId())) {
            notificationService.notify(assignee, project, task, NotificationType.TASK_ASSIGNED,
                    creator.getName() + " assigned you to \"" + task.getTitle() + "\".");
        }

        TaskResponse response = toResponse(task);
        eventPublisher.publishToProject(projectId, WsEventType.TASK_CREATED, response);
        return response;
    }

    public TaskResponse getTask(Long taskId, Long userId) {
        Task task = findTaskOrThrow(taskId);
        projectService.requireMembership(task.getProject().getId(), userId);
        return toResponse(task);
    }

    public List<TaskResponse> listForProject(Long projectId, Long userId) {
        projectService.requireMembership(projectId, userId);
        return taskRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TaskResponse updateTaskDetails(Long taskId, TaskUpdateRequest request, Long userId) {
        Task task = findTaskOrThrow(taskId);
        ProjectMember membership = projectService.requireMembership(task.getProject().getId(), userId);
        checkVersion(task, request.getVersion());

        boolean priorityChanged = task.getPriority() != request.getPriority();

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setPriority(request.getPriority());
        task.setDueDate(request.getDueDate());
        task = taskRepository.save(task);

        User actor = membership.getUser();
        if (priorityChanged) {
            activityService.record(task.getProject(), actor, task, ActivityType.TASK_PRIORITY_CHANGED,
                    actor.getName() + " changed the priority of \"" + task.getTitle() + "\" to " + task.getPriority() + ".");
        } else {
            activityService.record(task.getProject(), actor, task, ActivityType.TASK_UPDATED,
                    actor.getName() + " updated \"" + task.getTitle() + "\".");
        }

        TaskResponse response = toResponse(task);
        eventPublisher.publishToProject(task.getProject().getId(), WsEventType.TASK_UPDATED, response);
        return response;
    }

    @Transactional
    public TaskResponse changeStatus(Long taskId, TaskStatusUpdateRequest request, Long userId) {
        Task task = findTaskOrThrow(taskId);
        ProjectMember membership = projectService.requireMembership(task.getProject().getId(), userId);
        checkVersion(task, request.getVersion());

        TaskStatus oldStatus = task.getStatus();
        TaskStatus newStatus = request.getStatus();

        if (oldStatus == newStatus) {
            return toResponse(task); // no-op, nothing to record
        }

        task.setStatus(newStatus);
        task = taskRepository.save(task);

        User actor = membership.getUser();
        activityService.record(task.getProject(), actor, task, ActivityType.TASK_STATUS_CHANGED,
                actor.getName() + " moved \"" + task.getTitle() + "\" from " + oldStatus + " to " + newStatus + ".");

        if (newStatus == TaskStatus.DONE) {
            activityService.record(task.getProject(), actor, task, ActivityType.TASK_COMPLETED,
                    actor.getName() + " completed \"" + task.getTitle() + "\".");
        }

        if (task.getAssignee() != null && !task.getAssignee().getId().equals(actor.getId())) {
            notificationService.notify(task.getAssignee(), task.getProject(), task, NotificationType.TASK_STATUS_CHANGED,
                    actor.getName() + " moved \"" + task.getTitle() + "\" to " + newStatus + ".");
        }

        TaskResponse response = toResponse(task);
        eventPublisher.publishToProject(task.getProject().getId(), WsEventType.TASK_STATUS_CHANGED, response);
        return response;
    }

    @Transactional
    public TaskResponse assignTask(Long taskId, TaskAssignRequest request, Long userId) {
        Task task = findTaskOrThrow(taskId);
        ProjectMember membership = projectService.requireMembership(task.getProject().getId(), userId);

        User newAssignee = null;
        if (request.getAssigneeId() != null) {
            newAssignee = resolveAndValidateAssignee(task.getProject().getId(), request.getAssigneeId());
        }

        task.setAssignee(newAssignee);
        task = taskRepository.save(task);

        User actor = membership.getUser();
        String message = newAssignee != null
                ? actor.getName() + " assigned \"" + task.getTitle() + "\" to " + newAssignee.getName() + "."
                : actor.getName() + " unassigned \"" + task.getTitle() + "\".";
        activityService.record(task.getProject(), actor, task, ActivityType.TASK_ASSIGNED, message);

        if (newAssignee != null && !newAssignee.getId().equals(actor.getId())) {
            notificationService.notify(newAssignee, task.getProject(), task, NotificationType.TASK_ASSIGNED,
                    actor.getName() + " assigned you to \"" + task.getTitle() + "\".");
        }

        TaskResponse response = toResponse(task);
        eventPublisher.publishToProject(task.getProject().getId(), WsEventType.TASK_ASSIGNED, response);
        return response;
    }

    @Transactional
    public void deleteTask(Long taskId, Long userId) {
        Task task = findTaskOrThrow(taskId);
        ProjectMember membership = projectService.requireMembership(task.getProject().getId(), userId);

        boolean isCreator = task.getCreator().getId().equals(userId);
        if (!isCreator) {
            projectService.requireAdminOrOwner(membership); // only creator, or ADMIN/OWNER, may delete
        }

        Long projectId = task.getProject().getId();
        taskRepository.delete(task);
        eventPublisher.publishToProject(projectId, WsEventType.TASK_DELETED, taskId);
    }

    /** Tasks assigned to the given user, across every project they belong to. */
    public List<TaskResponse> listMyTasks(Long userId) {
        return taskRepository.findByAssigneeId(userId).stream().map(this::toResponse).toList();
    }

    /** Tasks created by the given user. */
    public List<TaskResponse> listCreatedByMe(Long userId) {
        return taskRepository.findByCreatorId(userId).stream().map(this::toResponse).toList();
    }

    /** Overdue tasks (across all projects) assigned to the given user. */
    public List<TaskResponse> listMyOverdue(Long userId) {
        return taskRepository.findOverdueForUser(userId, LocalDate.now()).stream().map(this::toResponse).toList();
    }

    // ---- helpers ----

    Task findTaskOrThrow(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
    }

    private User resolveAndValidateAssignee(Long projectId, Long assigneeId) {
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + assigneeId));
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, assigneeId)) {
            throw new BadRequestException("Cannot assign a task to someone who is not a project member");
        }
        return assignee;
    }

    /**
     * Optimistic concurrency check: if the version the client last saw no longer
     * matches what's currently in the database, someone else changed this task
     * in between - reject with 409 instead of silently overwriting their edit.
     */
    private void checkVersion(Task task, Long clientVersion) {
        if (!Objects.equals(task.getVersion(), clientVersion)) {
            throw new ConflictException(
                    "This task was changed by someone else since you last loaded it. Please refresh and try again.");
        }
    }

    TaskResponse toResponse(Task task) {
        boolean overdue = task.getDueDate() != null
                && task.getDueDate().isBefore(LocalDate.now())
                && task.getStatus() != TaskStatus.DONE;

        return new TaskResponse(
                task.getId(),
                task.getProject().getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getAssignee() != null ? UserResponse.from(task.getAssignee()) : null,
                UserResponse.from(task.getCreator()),
                task.getDueDate(),
                overdue,
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getVersion()
        );
    }
}
