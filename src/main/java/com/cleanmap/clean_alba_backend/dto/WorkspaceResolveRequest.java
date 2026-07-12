package com.cleanmap.clean_alba_backend.dto;

import java.math.BigDecimal;

// 카카오 신규 장소를 workspace로 변환(중복 시 재사용)하기 위한 요청 본문.
public record WorkspaceResolveRequest(
    String kakaoPlaceId,
    String name,
    String address,
    String category,
    BigDecimal latitude,
    BigDecimal longitude
) {
}
