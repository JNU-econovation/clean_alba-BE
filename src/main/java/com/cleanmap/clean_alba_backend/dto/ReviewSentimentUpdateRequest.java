package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.ReviewSentiment;

public record ReviewSentimentUpdateRequest(
        ReviewSentiment sentiment
) {
}
