package com.collabplatform.controller;

import com.collabplatform.dto.comment.CommentRequest;
import com.collabplatform.dto.comment.CommentResponse;
import com.collabplatform.security.UserPrincipal;
import com.collabplatform.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks/{taskId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    public ResponseEntity<CommentResponse> add(@PathVariable Long taskId,
                                                @Valid @RequestBody CommentRequest request,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.addComment(taskId, request, principal.getId()));
    }

    @GetMapping
    public ResponseEntity<List<CommentResponse>> list(@PathVariable Long taskId,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(commentService.listForTask(taskId, principal.getId()));
    }
}
