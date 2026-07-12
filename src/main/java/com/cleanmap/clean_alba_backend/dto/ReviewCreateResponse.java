package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.ReviewStatus;

import java.time.LocalDateTime;

public record ReviewCreateResponse(
        Long reviewId,
        Long workspaceId,
        ReviewStatus status,
        LocalDateTime createdAt
) {
    public static ReviewCreateResponse from(Review review) {
        return new ReviewCreateResponse(
                review.getReviewId(),
                review.getWorkspace().getWorkspaceId(),
                review.getStatus(),
                review.getCreatedAt()
        );
    }
}
