package com.cleanmap.clean_alba_backend.controller;

import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;
import com.cleanmap.clean_alba_backend.dto.WorkspaceListResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspaceSummaryResponse;
import com.cleanmap.clean_alba_backend.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @GetMapping
    public List<WorkspaceListResponse> getWorkspaceList(
        @RequestParam(required = false) WorkspaceStatus status,
        @RequestParam(required = false) String keyword
    ) {
        return workspaceService.getWorkspaceList(status, keyword);
    }

    @GetMapping("/{workspaceId}/summary")
    public WorkspaceSummaryResponse getWorkspaceSummary(
        @PathVariable Long workspaceId
    ) {
        return workspaceService.getWorkspaceSummary(workspaceId);
    }
}
