package com.cleanmap.clean_alba_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

// 카카오 토큰 발급 API의 응답을 담는 클래스
// KakaoService.getAccessToken()에서 카카오 응답을 이 형태로 자동 변환해서 받음
// 카카오는 JSON 키를 snake_case(access_token)로 주기 때문에
// @JsonProperty로 자바 필드명(camelCase)과 매핑해줘야 함
@Getter
public class KakaoTokenDto {

    @JsonProperty("access_token")   // 카카오가 주는 액세스 토큰 (유저 정보 조회에 사용)
    private String accessToken;

    @JsonProperty("token_type")     // 토큰 타입 (항상 "bearer")
    private String tokenType;

    @JsonProperty("refresh_token")  // 액세스 토큰 재발급용 토큰 (지금은 사용 안 함)
    private String refresh_token;

    @JsonProperty("expires_in")     // 액세스 토큰 만료 시간 (초 단위)
    private int expiresIn;
}
