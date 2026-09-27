package com.collabplatform.dto.task;

import com.collabplatform.entity.enums.TaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskStatusUpdateRequest {

    @NotNull
    private TaskStatus status;

    /**
     * The version the client last saw. Used for an optimistic-locking check:
     * if it no longer matches the row in the database, someone else already
     * changed this task and we reject the update with 409 Conflict rather
     * than silently overwriting their change.
     */
    @NotNull
    private Long version;
}
