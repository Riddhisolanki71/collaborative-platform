package com.collabplatform.controller;

import com.collabplatform.dto.project.ProjectMemberRequest;
import com.collabplatform.dto.project.ProjectMemberResponse;
import com.collabplatform.dto.project.ProjectRequest;
import com.collabplatform.dto.project.ProjectResponse;
import com.collabplatform.security.UserPrincipal;
import com.collabplatform.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest request,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.createProject(request, principal.getId()));
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> listMine(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(projectService.listForUser(principal.getId()));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> get(@PathVariable Long projectId,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(projectService.getProject(projectId, principal.getId()));
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> update(@PathVariable Long projectId,
                                                   @Valid @RequestBody ProjectRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(projectService.updateProject(projectId, request, principal.getId()));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@PathVariable Long projectId,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        projectService.deleteProject(projectId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/members")
    public ResponseEntity<List<ProjectMemberResponse>> listMembers(@PathVariable Long projectId,
                                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(projectService.listMembers(projectId, principal.getId()));
    }

    @PostMapping("/{projectId}/members")
    public ResponseEntity<ProjectMemberResponse> addMember(@PathVariable Long projectId,
                                                            @Valid @RequestBody ProjectMemberRequest request,
                                                            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.addMember(projectId, request, principal.getId()));
    }

    @DeleteMapping("/{projectId}/members/{memberUserId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long projectId,
                                              @PathVariable Long memberUserId,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        projectService.removeMember(projectId, memberUserId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
