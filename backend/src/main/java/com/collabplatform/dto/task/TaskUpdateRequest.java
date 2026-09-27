package com.collabplatform.dto.task;

import com.collabplatform.entity.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** Used for editing title/description/priority/due date (not status - see TaskStatusUpdateRequest). */
@Getter
@Setter
public class TaskUpdateRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 4000)
    private String description;

    @NotNull
    private Priority priority;

    private LocalDate dueDate;

    @NotNull
    private Long version;
}
