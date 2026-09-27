package com.collabplatform.dto.project;

import com.collabplatform.dto.user.UserResponse;
import com.collabplatform.entity.enums.ProjectRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class ProjectMemberResponse {
    private Long id;
    private UserResponse user;
    private ProjectRole role;
    private Instant joinedAt;
}
