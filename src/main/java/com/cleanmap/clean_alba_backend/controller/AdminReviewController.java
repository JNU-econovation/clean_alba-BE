package com.cleanmap.clean_alba_backend.controller;

import com.cleanmap.clean_alba_backend.dto.AdminReviewResponse;
import com.cleanmap.clean_alba_backend.dto.AdminStatsResponse;
import com.cleanmap.clean_alba_backend.dto.PagedResponse;
import com.cleanmap.clean_alba_backend.dto.ReviewContentUpdateRequest;
import com.cleanmap.clean_alba_backend.dto.ReviewContentUpdateResponse;
import com.cleanmap.clean_alba_backend.dto.ReviewSentimentUpdateRequest;
import com.cleanmap.clean_alba_backend.dto.ReviewSentimentUpdateResponse;
import com.cleanmap.clean_alba_backend.dto.ReviewStatusUpdateRequest;
import com.cleanmap.clean_alba_backend.dto.ReviewStatusUpdateResponse;
import com.cleanmap.clean_alba_backend.domain.ReviewAttachment;
import com.cleanmap.clean_alba_backend.service.AdminReviewService;
import com.cleanmap.clean_alba_backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminReviewController {

    private final AdminReviewService adminReviewService;
    private final AuthService authService;

    @GetMapping("/reviews")
    public PagedResponse<AdminReviewResponse> getReviews(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        authService.requireAdmin(authorizationHeader);
        return adminReviewService.getReviews(status, page, size);
    }

    @GetMapping("/reviews/{reviewId}")
    public AdminReviewResponse getReview(
            @PathVariable Long reviewId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        authService.requireAdmin(authorizationHeader);
        return adminReviewService.getReview(reviewId);
    }

    @GetMapping("/reviews/{reviewId}/attachments/{attachmentId}")
    public ResponseEntity<StreamingResponseBody> downloadAttachment(
            @PathVariable Long reviewId,
            @PathVariable Long attachmentId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        authService.requireAdmin(authorizationHeader);
        AdminReviewService.AttachmentDownload download = adminReviewService.openAttachment(reviewId, attachmentId);
        ReviewAttachment attachment = download.attachment();
        return ResponseEntity.ok()
                .contentType(safeMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(attachment.getOriginalFileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentLength(attachment.getSize())
                .body(outputStream -> {
                    try (var inputStream = download.content()) {
                        inputStream.transferTo(outputStream);
                    }
                });
    }

    private MediaType safeMediaType(String contentType) {
        try {
            return contentType == null || contentType.isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    @PatchMapping("/reviews/{reviewId}/status")
    public ReviewStatusUpdateResponse updateStatus(
            @PathVariable Long reviewId,
            @RequestBody ReviewStatusUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        authService.requireAdmin(authorizationHeader);
        return adminReviewService.updateStatus(reviewId, request.status());
    }

    @PatchMapping("/reviews/{reviewId}/content")
    public ReviewContentUpdateResponse updateContent(
            @PathVariable Long reviewId,
            @RequestBody ReviewContentUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        authService.requireAdmin(authorizationHeader);
        return adminReviewService.updateContent(reviewId, request.content());
    }

    @PatchMapping("/reviews/{reviewId}/sentiment")
    public ReviewSentimentUpdateResponse updateSentiment(
            @PathVariable Long reviewId,
            @RequestBody ReviewSentimentUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        authService.requireAdmin(authorizationHeader);
        return adminReviewService.updateSentiment(reviewId, request.sentiment());
    }

    @GetMapping("/stats")
    public AdminStatsResponse getStats(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        authService.requireAdmin(authorizationHeader);
        return adminReviewService.getStats();
    }
}
