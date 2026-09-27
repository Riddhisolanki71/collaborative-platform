package com.collabplatform.controller;

import com.collabplatform.dto.activity.ActivityResponse;
import com.collabplatform.security.UserPrincipal;
import com.collabplatform.service.ActivityService;
import com.collabplatform.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;
    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<List<ActivityResponse>> list(@PathVariable Long projectId,
                                                         @AuthenticationPrincipal UserPrincipal principal) {
        projectService.requireMembership(projectId, principal.getId()); // 403 if not a member
        return ResponseEntity.ok(activityService.listForProject(projectId));
    }
}
