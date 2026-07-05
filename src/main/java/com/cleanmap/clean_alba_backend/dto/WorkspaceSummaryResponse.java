package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;
import java.math.BigDecimal;

public record WorkspaceSummaryResponse(
    Long workspaceId,
    String name,
    String address,
    String category,
    String district,
    BigDecimal latitude,
    BigDecimal longitude,
    Integer cleanScore,
    WorkspaceStatus status
) {
    public static WorkspaceSummaryResponse from(Workspace workspace) {
        Double raw = workspace.getCleanScore();
        Integer displayScore = (raw == null) ? null : (int) Math.round(raw);
        WorkspaceStatus status = (displayScore == null) ? null : WorkspaceStatus.fromScore(displayScore);
        return new WorkspaceSummaryResponse(
            workspace.getWorkspaceId(),
            workspace.getName(),
            workspace.getAddress(),
            workspace.getCategory(),
            workspace.getDistrict(),
            workspace.getLatitude(),
            workspace.getLongitude(),
            displayScore,
            status
        );
    }
}
