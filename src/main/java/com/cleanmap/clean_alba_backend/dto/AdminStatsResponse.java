package com.cleanmap.clean_alba_backend.dto;

public record AdminStatsResponse(
        long totalReviews,
        long pendingReviews,
        long approvedReviews,
        long rejectedReviews,
        long totalWorkspaces
) {
}
