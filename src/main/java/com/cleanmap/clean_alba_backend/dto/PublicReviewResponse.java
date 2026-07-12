package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.Review;

import java.time.LocalDateTime;

public record PublicReviewResponse(
        Long reviewId,
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
        LocalDateTime createdAt
) {
    public static PublicReviewResponse from(Review review) {
        return new PublicReviewResponse(
                review.getReviewId(),
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
                review.getCreatedAt()
        );
    }
}
