package com.collabplatform.dto.task;

import com.collabplatform.entity.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class TaskRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200)
    private String title;

    @Size(max = 4000)
    private String description;

    private Priority priority; // defaults to MEDIUM if null

    private Long assigneeId; // optional at creation time

    private LocalDate dueDate;
}
