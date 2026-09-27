package com.collabplatform.dto.project;

import com.collabplatform.entity.enums.ProjectRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectMemberRequest {

    @NotBlank
    @Email
    private String email; // add an existing user to the project by email

    @NotNull
    private ProjectRole role;
}
