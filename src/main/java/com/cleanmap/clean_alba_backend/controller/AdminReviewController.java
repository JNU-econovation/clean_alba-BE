package com.cleanmap.clean_alba_backend.controller;

import com.cleanmap.clean_alba_backend.dto.AdminReviewResponse;
import com.cleanmap.clean_alba_backend.dto.AdminStatsResponse;
import com.cleanmap.clean_alba_backend.dto.PagedResponse;
import com.cleanmap.clean_alba_backend.dto.ReviewStatusUpdateRequest;
import com.cleanmap.clean_alba_backend.dto.ReviewStatusUpdateResponse;
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

    @PatchMapping("/reviews/{reviewId}/status")
    public ReviewStatusUpdateResponse updateStatus(
            @PathVariable Long reviewId,
            @RequestBody ReviewStatusUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        authService.requireAdmin(authorizationHeader);
        return adminReviewService.updateStatus(reviewId, request.status());
    }

    @GetMapping("/stats")
    public AdminStatsResponse getStats(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        authService.requireAdmin(authorizationHeader);
        return adminReviewService.getStats();
    }
}
