package com.collabplatform.dto.task;

import com.collabplatform.dto.user.UserResponse;
import com.collabplatform.entity.enums.Priority;
import com.collabplatform.entity.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class TaskResponse {
    private Long id;
    private Long projectId;
    private String title;
    private String description;
    private TaskStatus status;
    private Priority priority;
    private UserResponse assignee;
    private UserResponse creator;
    private LocalDate dueDate;
    private boolean overdue;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
