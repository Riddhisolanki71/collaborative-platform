package com.collabplatform.service;

import com.collabplatform.dto.comment.CommentRequest;
import com.collabplatform.dto.comment.CommentResponse;
import com.collabplatform.dto.user.UserResponse;
import com.collabplatform.dto.ws.WsEventType;
import com.collabplatform.entity.Comment;
import com.collabplatform.entity.ProjectMember;
import com.collabplatform.entity.Task;
import com.collabplatform.entity.User;
import com.collabplatform.entity.enums.ActivityType;
import com.collabplatform.entity.enums.NotificationType;
import com.collabplatform.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final ActivityService activityService;
    private final NotificationService notificationService;
    private final WebSocketEventPublisher eventPublisher;

    @Transactional
    public CommentResponse addComment(Long taskId, CommentRequest request, Long authorId) {
        Task task = taskService.findTaskOrThrow(taskId);
        ProjectMember membership = projectService.requireMembership(task.getProject().getId(), authorId);
        User author = membership.getUser();

        Comment comment = Comment.builder()
                .task(task)
                .author(author)
                .content(request.getContent())
                .build();
        comment = commentRepository.save(comment);

        activityService.record(task.getProject(), author, task, ActivityType.COMMENT_ADDED,
                author.getName() + " commented on \"" + task.getTitle() + "\".");

        // Notify the assignee and the task creator (if different from the commenter) that a new comment landed.
        notifyIfDifferent(task.getAssignee(), author, task, "commented on a task assigned to you");
        notifyIfDifferent(task.getCreator(), author, task, "commented on a task you created");

        CommentResponse response = toResponse(comment);
        eventPublisher.publishToProject(task.getProject().getId(), WsEventType.COMMENT_ADDED, response);
        return response;
    }

    public List<CommentResponse> listForTask(Long taskId, Long userId) {
        Task task = taskService.findTaskOrThrow(taskId);
        projectService.requireMembership(task.getProject().getId(), userId);
        return commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(this::toResponse)
                .toList();
    }

    private void notifyIfDifferent(User recipient, User author, Task task, String verb) {
        if (recipient != null && !recipient.getId().equals(author.getId())) {
            notificationService.notify(recipient, task.getProject(), task, NotificationType.COMMENT_ADDED,
                    author.getName() + " " + verb + ": \"" + task.getTitle() + "\".");
        }
    }

    private CommentResponse toResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getTask().getId(),
                UserResponse.from(comment.getAuthor()),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
