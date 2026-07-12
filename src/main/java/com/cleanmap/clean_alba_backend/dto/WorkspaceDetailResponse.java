package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;

import java.math.BigDecimal;
import java.util.List;

public record WorkspaceDetailResponse(
        Long workspaceId,
        String name,
        String address,
        String category,
        String district,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer cleanScore,
        WorkspaceStatus status,
        long reviewCount,
        List<ChecklistStatResponse> checklistStats,
        List<PublicReviewResponse> reviews
) {
}
