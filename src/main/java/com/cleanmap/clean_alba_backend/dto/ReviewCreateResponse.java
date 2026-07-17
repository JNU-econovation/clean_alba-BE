package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.DayType;
import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.ReviewStatus;
import com.cleanmap.clean_alba_backend.domain.TimeSlot;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewCreateResponse(
        Long reviewId,
        Long workspaceId,
        ReviewStatus status,
        String content,
        Integer coworkerCount,
        DayType dayType,
        TimeSlot timeSlot,
        List<String> violationItems,
        LocalDateTime createdAt
) {
    public static ReviewCreateResponse from(Review review) {
        return new ReviewCreateResponse(
                review.getReviewId(),
                review.getWorkspace().getWorkspaceId(),
                review.getStatus(),
                review.getContent(),
                review.getCoworkerCount(),
                review.getDayType(),
                review.getTimeSlot(),
                review.violationItems(),
                review.getCreatedAt()
        );
    }
}
