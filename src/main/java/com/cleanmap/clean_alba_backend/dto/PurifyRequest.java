package com.cleanmap.clean_alba_backend.dto;

// 후기 순화 요청 본문. 순화할 원문 텍스트만 받는다.
public record PurifyRequest(
    String reviewText
) {
}
