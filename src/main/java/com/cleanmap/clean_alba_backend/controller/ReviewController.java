package com.cleanmap.clean_alba_backend.controller;

import com.cleanmap.clean_alba_backend.dto.PurifyRequest;
import com.cleanmap.clean_alba_backend.service.PurifyService;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final PurifyService purifyService;

    // POST /reviews/purify-preview
    // 후기 원문 → 리스크 평가 + 순화된 3가지 버전(JSON) 반환. 제출 전 미리보기용.
    @PostMapping("/purify-preview")
    public JsonNode purifyPreview(@RequestBody PurifyRequest request) {
        return purifyService.purify(request.reviewText());
    }
}
