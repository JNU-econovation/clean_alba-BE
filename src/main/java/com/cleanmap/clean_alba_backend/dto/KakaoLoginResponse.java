package com.cleanmap.clean_alba_backend.dto;

import lombok.Getter;
import lombok.Setter;

// 카카오 로그인 완료 후 프론트에게 돌려주는 최종 응답 형태
// KakaoController에서 이 객체에 값을 담아 return하면
// 스프링이 자동으로 아래 JSON 형태로 변환해서 응답함:
// {
//   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
//   "userEmail": "frontend_master@kakao.com",
//   "nickname": "다은"
// }
@Getter
@Setter
public class KakaoLoginResponse {

    private String token;     // 우리 서비스가 발급한 JWT (프론트가 이후 API 요청 시 사용)
    private String userEmail; // 카카오에서 받아온 이메일
    private String nickname;  // 카카오에서 받아온 닉네임

}
