package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.ReviewSentiment;

import java.time.LocalDateTime;

public record ReviewSentimentUpdateResponse(
        Long reviewId,
        ReviewSentiment sentiment,
        LocalDateTime updatedAt
) {
}
