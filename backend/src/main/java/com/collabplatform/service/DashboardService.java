package com.collabplatform.service;

import com.collabplatform.dto.dashboard.DashboardResponse;
import com.collabplatform.dto.task.TaskResponse;
import com.collabplatform.entity.enums.Priority;
import com.collabplatform.entity.enums.TaskStatus;
import com.collabplatform.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Computes the simple, project-scoped dashboard: task counts by status, overdue
 * tasks, high-priority tasks, and tasks assigned to the requesting user.
 * Deliberately avoids heavier analytics (velocity, burndown, etc.) - see README
 * "Future improvements" for where those would be added later.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TaskRepository taskRepository;
    private final ProjectService projectService;
    private final TaskService taskService;

    public DashboardResponse getDashboard(Long projectId, Long userId) {
        projectService.requireMembership(projectId, userId);

        long total = taskRepository.countByProjectId(projectId);
        long todo = taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.TODO);
        long inProgress = taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.IN_PROGRESS);
        long done = taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.DONE);

        List<TaskResponse> overdue = taskRepository.findOverdueForProject(projectId, LocalDate.now()).stream()
                .map(taskService::toResponse)
                .toList();

        List<TaskResponse> highPriority = taskRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .filter(t -> (t.getPriority() == Priority.HIGH || t.getPriority() == Priority.URGENT)
                        && t.getStatus() != TaskStatus.DONE)
                .map(taskService::toResponse)
                .toList();

        List<TaskResponse> myTasks = taskRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .filter(t -> t.getAssignee() != null && t.getAssignee().getId().equals(userId))
                .map(taskService::toResponse)
                .toList();

        return new DashboardResponse(total, todo, inProgress, done, overdue, highPriority, myTasks);
    }
}
