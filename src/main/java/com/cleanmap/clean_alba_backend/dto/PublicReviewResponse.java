package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.DayType;
import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.TimeSlot;

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
        DayType dayType,
        TimeSlot timeSlot,
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
                review.getDayType(),
                review.getTimeSlot(),
                review.getContent(),
                review.getCreatedAt()
        );
    }
}
