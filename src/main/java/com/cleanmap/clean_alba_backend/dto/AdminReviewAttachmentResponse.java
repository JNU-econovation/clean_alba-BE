package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.ReviewAttachment;

public record AdminReviewAttachmentResponse(
        Long attachmentId,
        String fileName,
        String contentType,
        long size
) {
    public static AdminReviewAttachmentResponse from(ReviewAttachment attachment) {
        return new AdminReviewAttachmentResponse(
                attachment.getAttachmentId(),
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getSize()
        );
    }
}
