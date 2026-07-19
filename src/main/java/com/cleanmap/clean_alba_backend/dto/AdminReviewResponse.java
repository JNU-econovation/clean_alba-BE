package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.ReviewStatus;

import java.time.LocalDateTime;
import java.util.List;

public record AdminReviewResponse(
        Long reviewId,
        Long workspaceId,
        String workspaceName,
        String authorEmail,
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
        LocalDateTime createdAt,
        long attachmentCount,
        List<AdminReviewAttachmentResponse> attachments
) {
    public static AdminReviewResponse from(Review review, long attachmentCount) {
        return from(review, attachmentCount, List.of());
    }

    public static AdminReviewResponse from(
            Review review,
            List<AdminReviewAttachmentResponse> attachments
    ) {
        return from(review, attachments.size(), attachments);
    }

    private static AdminReviewResponse from(
            Review review,
            long attachmentCount,
            List<AdminReviewAttachmentResponse> attachments
    ) {
        return new AdminReviewResponse(
                review.getReviewId(),
                review.getWorkspace().getWorkspaceId(),
                review.getWorkspace().getName(),
                review.getAuthorEmail(),
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
                review.getCreatedAt(),
                attachmentCount,
                List.copyOf(attachments)
        );
    }
}
