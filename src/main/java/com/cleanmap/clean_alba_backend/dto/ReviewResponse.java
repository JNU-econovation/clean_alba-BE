package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.ReviewStatus;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long reviewId,
        Long workspaceId,
        String workspaceName,
        boolean contractViolation,
        boolean minimumWageViolation,
        boolean weeklyAllowanceViolation,
        boolean breakTimeViolation,
        boolean wageDelayViolation,
        boolean scheduleChangeViolation,
        boolean substituteCoercionViolation,
        boolean overtimePayViolation,
        Integer coworkerCount,
        String content,
        ReviewStatus status,
        LocalDateTime createdAt
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getReviewId(),
                review.getWorkspace().getWorkspaceId(),
                review.getWorkspace().getName(),
                review.isContractViolation(),
                review.isMinimumWageViolation(),
                review.isWeeklyAllowanceViolation(),
                review.isBreakTimeViolation(),
                review.isWageDelayViolation(),
                review.isScheduleChangeViolation(),
                review.isSubstituteCoercionViolation(),
                review.isOvertimePayViolation(),
                review.getCoworkerCount(),
                review.getContent(),
                review.getStatus(),
                review.getCreatedAt()
        );
    }
}
