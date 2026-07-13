package com.cleanmap.clean_alba_backend.service;

import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.dto.WorkspaceResolveRequest;
import com.cleanmap.clean_alba_backend.dto.WorkspaceResolveResponse;
import com.cleanmap.clean_alba_backend.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceResolveWriter {

    private final WorkspaceRepository workspaceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WorkspaceResolveResponse create(WorkspaceResolveRequest request, String kakaoPlaceId) {
        Workspace workspace = new Workspace(
                request.name().trim(),
                request.address().trim(),
                request.category().trim(),
                null,
                request.latitude(),
                request.longitude(),
                kakaoPlaceId);
        Workspace saved = workspaceRepository.saveAndFlush(workspace);
        return new WorkspaceResolveResponse(saved.getWorkspaceId(), true);
    }
}
