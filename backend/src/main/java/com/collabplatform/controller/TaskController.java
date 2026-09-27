package com.collabplatform.controller;

import com.collabplatform.dto.task.TaskAssignRequest;
import com.collabplatform.dto.task.TaskRequest;
import com.collabplatform.dto.task.TaskResponse;
import com.collabplatform.dto.task.TaskStatusUpdateRequest;
import com.collabplatform.dto.task.TaskUpdateRequest;
import com.collabplatform.security.UserPrincipal;
import com.collabplatform.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping("/api/projects/{projectId}/tasks")
    public ResponseEntity<TaskResponse> create(@PathVariable Long projectId,
                                                @Valid @RequestBody TaskRequest request,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.createTask(projectId, request, principal.getId()));
    }

    @GetMapping("/api/projects/{projectId}/tasks")
    public ResponseEntity<List<TaskResponse>> listForProject(@PathVariable Long projectId,
                                                              @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(taskService.listForProject(projectId, principal.getId()));
    }

    @GetMapping("/api/tasks/{taskId}")
    public ResponseEntity<TaskResponse> get(@PathVariable Long taskId,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(taskService.getTask(taskId, principal.getId()));
    }

    @PutMapping("/api/tasks/{taskId}")
    public ResponseEntity<TaskResponse> update(@PathVariable Long taskId,
                                                @Valid @RequestBody TaskUpdateRequest request,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(taskService.updateTaskDetails(taskId, request, principal.getId()));
    }

    @PatchMapping("/api/tasks/{taskId}/status")
    public ResponseEntity<TaskResponse> changeStatus(@PathVariable Long taskId,
                                                      @Valid @RequestBody TaskStatusUpdateRequest request,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(taskService.changeStatus(taskId, request, principal.getId()));
    }

    @PatchMapping("/api/tasks/{taskId}/assign")
    public ResponseEntity<TaskResponse> assign(@PathVariable Long taskId,
                                                @Valid @RequestBody TaskAssignRequest request,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(taskService.assignTask(taskId, request, principal.getId()));
    }

    @DeleteMapping("/api/tasks/{taskId}")
    public ResponseEntity<Void> delete(@PathVariable Long taskId,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        taskService.deleteTask(taskId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/tasks/mine/assigned")
    public ResponseEntity<List<TaskResponse>> myAssigned(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(taskService.listMyTasks(principal.getId()));
    }

    @GetMapping("/api/tasks/mine/created")
    public ResponseEntity<List<TaskResponse>> myCreated(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(taskService.listCreatedByMe(principal.getId()));
    }

    @GetMapping("/api/tasks/mine/overdue")
    public ResponseEntity<List<TaskResponse>> myOverdue(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(taskService.listMyOverdue(principal.getId()));
    }
}
