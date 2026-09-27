package com.collabplatform.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectRequest {

    @NotBlank(message = "Project name is required")
    @Size(max = 150)
    private String name;

    @Size(max = 2000)
    private String description;
}
