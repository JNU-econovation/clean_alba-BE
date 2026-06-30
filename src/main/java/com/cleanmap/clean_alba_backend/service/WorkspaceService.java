package com.cleanmap.clean_alba_backend.service;

import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;
import com.cleanmap.clean_alba_backend.dto.WorkspaceListResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspaceSummaryResponse;
import com.cleanmap.clean_alba_backend.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;

    @Transactional(readOnly = true)
    public List<WorkspaceListResponse> getWorkspaceList(WorkspaceStatus status, String keyword) {
        String normalizedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        List<Workspace> workspaces = workspaceRepository.search(status, normalizedKeyword);

        return workspaces.stream()
                .map(WorkspaceListResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceSummaryResponse getWorkspaceSummary(Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Workspace not found."));

        return WorkspaceSummaryResponse.from(workspace);
    }
}
