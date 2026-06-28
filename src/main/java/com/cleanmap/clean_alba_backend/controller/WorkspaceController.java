package com.cleanmap.clean_alba_backend.controller;

import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;
import com.cleanmap.clean_alba_backend.dto.WorkspaceListResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspacePasswordRequest;
import com.cleanmap.clean_alba_backend.dto.WorkspacePasswordResponse;
import com.cleanmap.clean_alba_backend.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        @RequestParam(required = false) WorkspaceStatus status
    ) {
        return workspaceService.getWorkspaceList(status);
    }

    @PostMapping("/{workspaceId}/match-password")
    public WorkspacePasswordResponse matchPassword(
            @PathVariable Long workspaceId,
            @RequestBody WorkspacePasswordRequest request
    ) {
        return workspaceService.matchPassword(workspaceId, request);
    }
}
