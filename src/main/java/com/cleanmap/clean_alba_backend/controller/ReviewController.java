package com.cleanmap.clean_alba_backend.controller;

import com.cleanmap.clean_alba_backend.dto.PurifyRequest;
import com.cleanmap.clean_alba_backend.dto.ReviewAttachmentResponse;
import com.cleanmap.clean_alba_backend.service.AuthService;
import com.cleanmap.clean_alba_backend.service.PurifyService;
import com.cleanmap.clean_alba_backend.service.ReviewService;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;

/** 후기 제출 전에 법적 위험 표현과 순화 후보를 미리 확인하는 API를 제공한다. */
@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final PurifyService purifyService;
    private final ReviewService reviewService;
    private final AuthService authService;

    // POST /reviews/purify-preview
    // 후기 원문 → 리스크 평가 + 순화된 3가지 버전(JSON) 반환. 제출 전 미리보기용. (로그인 필요)
    @PostMapping("/purify-preview")
    public JsonNode purifyPreview(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody PurifyRequest request
    ) {
        authService.authenticate(authorizationHeader);
        return purifyService.purify(request.reviewText());
    }

    @PostMapping("/{reviewId}/attachments")
    public ResponseEntity<ReviewAttachmentResponse> addAttachment(
            @PathVariable Long reviewId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestPart("file") MultipartFile file
    ) {
        AuthService.AuthenticatedUser user = authService.authenticate(authorizationHeader);
        return ResponseEntity.status(201).body(reviewService.addAttachment(reviewId, file, user));
    }
}
