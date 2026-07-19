package com.cleanmap.clean_alba_backend.dto;

import java.time.LocalDateTime;

public record ReviewContentUpdateResponse(
        Long reviewId,
        String content,
        LocalDateTime updatedAt
) {
}
