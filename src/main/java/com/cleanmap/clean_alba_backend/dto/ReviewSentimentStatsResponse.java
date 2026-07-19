package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.ReviewSentiment;

import java.util.List;

public record ReviewSentimentStatsResponse(
        int positiveCount,
        int neutralCount,
        int negativeCount,
        int positiveRate,
        int neutralRate,
        int negativeRate
) {
    public static ReviewSentimentStatsResponse from(List<Review> approvedReviews) {
        int positiveCount = count(approvedReviews, ReviewSentiment.POSITIVE);
        int neutralCount = count(approvedReviews, ReviewSentiment.NEUTRAL);
        int negativeCount = count(approvedReviews, ReviewSentiment.NEGATIVE);
        int total = positiveCount + neutralCount + negativeCount;
        if (total == 0) {
            return new ReviewSentimentStatsResponse(0, 0, 0, 0, 0, 0);
        }
        return new ReviewSentimentStatsResponse(
                positiveCount,
                neutralCount,
                negativeCount,
                Math.round(positiveCount * 100.0f / total),
                Math.round(neutralCount * 100.0f / total),
                Math.round(negativeCount * 100.0f / total)
        );
    }

    public ReviewSentiment dominantSentiment() {
        int highestCount = Math.max(positiveCount, Math.max(neutralCount, negativeCount));
        if (highestCount == 0 || countHighest(highestCount) != 1) {
            return null;
        }
        if (positiveCount == highestCount) {
            return ReviewSentiment.POSITIVE;
        }
        return neutralCount == highestCount ? ReviewSentiment.NEUTRAL : ReviewSentiment.NEGATIVE;
    }

    private static int count(List<Review> reviews, ReviewSentiment sentiment) {
        return (int) reviews.stream().filter(review -> review.getSentiment() == sentiment).count();
    }

    private int countHighest(int highestCount) {
        int count = 0;
        if (positiveCount == highestCount) count++;
        if (neutralCount == highestCount) count++;
        if (negativeCount == highestCount) count++;
        return count;
    }
}
