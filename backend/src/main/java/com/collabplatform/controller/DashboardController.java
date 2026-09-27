package com.collabplatform.controller;

import com.collabplatform.dto.dashboard.DashboardResponse;
import com.collabplatform.security.UserPrincipal;
import com.collabplatform.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/api/projects/{projectId}/dashboard")
    public ResponseEntity<DashboardResponse> get(@PathVariable Long projectId,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dashboardService.getDashboard(projectId, principal.getId()));
    }
}
