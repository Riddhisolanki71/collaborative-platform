package com.collabplatform.dto.task;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskAssignRequest {
    // null = unassign
    private Long assigneeId;
}
