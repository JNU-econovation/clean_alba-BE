package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;
import java.math.BigDecimal;

public record WorkspaceListResponse(
    Long workspaceId,
    String name,
    String address,
    BigDecimal latitude,
    BigDecimal longitude,
    Integer cleanScore,
    WorkspaceStatus status
) {
    public static WorkspaceListResponse from(Workspace workspace) {
        return new WorkspaceListResponse(
            workspace.getWorkspaceId(),
            workspace.getName(),
            workspace.getAddress(),
            workspace.getLatitude(),
            workspace.getLongitude(),
            workspace.getCleanScore(),
            workspace.getStatus()
        );
    }
}
