package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.ReviewStatus;
import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;

public record ReviewStatusUpdateResponse(
        Long reviewId,
        ReviewStatus status,
        Integer cleanScore,
        WorkspaceStatus workspaceStatus
) {
}
