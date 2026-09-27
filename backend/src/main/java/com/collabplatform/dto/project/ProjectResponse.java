package com.collabplatform.dto.project;

import com.collabplatform.dto.user.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class ProjectResponse {
    private Long id;
    private String name;
    private String description;
    private UserResponse owner;
    private String myRole; // the requesting user's ProjectRole in this project
    private Instant createdAt;
    private Instant updatedAt;
}
