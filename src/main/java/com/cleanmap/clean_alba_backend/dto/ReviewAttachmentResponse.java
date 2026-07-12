package com.cleanmap.clean_alba_backend.dto;

public record ReviewAttachmentResponse(
        Long attachmentId,
        Long reviewId,
        String fileName,
        String contentType,
        long size
) {
}
