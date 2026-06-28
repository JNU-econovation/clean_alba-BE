package com.cleanmap.clean_alba_backend.service;

import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;
import com.cleanmap.clean_alba_backend.dto.WorkspaceListResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspacePasswordRequest;
import com.cleanmap.clean_alba_backend.dto.WorkspacePasswordResponse;
import com.cleanmap.clean_alba_backend.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<WorkspaceListResponse> getWorkspaceList(WorkspaceStatus status) {
        List<Workspace> workspaces = (status == null)
                ? workspaceRepository.findAllByOrderByCleanScoreDescWorkspaceIdAsc()
                : workspaceRepository.findByStatusOrderByCleanScoreDescWorkspaceIdAsc(status);

        return workspaces.stream()
                .map(WorkspaceListResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspacePasswordResponse matchPassword(
            Long workspaceId,
            WorkspacePasswordRequest request
    ) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Workspace not found."));

        boolean matched = workspace.getAccessPassword() != null
                && passwordEncoder.matches(
                        request.password(),
                        workspace.getAccessPassword()
                );

        return new WorkspacePasswordResponse(matched);
    }
}
