package com.collabplatform.dto.comment;

import com.collabplatform.dto.user.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class CommentResponse {
    private Long id;
    private Long taskId;
    private UserResponse author;
    private String content;
    private Instant createdAt;
}
