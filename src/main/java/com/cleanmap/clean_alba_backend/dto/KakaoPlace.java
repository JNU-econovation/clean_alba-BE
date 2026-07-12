package com.cleanmap.clean_alba_backend.dto;

import java.math.BigDecimal;

// 카카오 로컬 검색 결과를 우리 도메인 관점으로 정규화한 값.
// KakaoLocalSearchResponse(카카오 JSON 형태)에 대한 의존을 서비스 계층에서 분리한다.
public record KakaoPlace(
    String kakaoPlaceId,
    String name,
    String address,
    String category,
    BigDecimal latitude,
    BigDecimal longitude
) {
}
