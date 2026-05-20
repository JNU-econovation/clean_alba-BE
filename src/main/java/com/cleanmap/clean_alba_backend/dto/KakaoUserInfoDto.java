package com.cleanmap.clean_alba_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// 카카오 유저 정보 API의 응답을 담는 클래스
// KakaoService.getUserInfo()에서 카카오 응답을 이 형태로 자동 변환해서 받음
//
// 카카오 응답 JSON 구조:
// {
//   "id": 123456789,
//   "kakao_account": {
//     "profile": { "nickname": "다은" },
//     "email": "frontend_master@kakao.com"
//   }
// }
public class KakaoUserInfoDto {

    private Long id; // 카카오 고유 유저 ID

    // 카카오는 "kakao_account"로 주지만 자바 변수명은 kakaoAccount로 매핑
    @JsonProperty("kakao_account")
    private KakaoAccount kakaoAccount;

    public Long getId() { return id; }
    public KakaoAccount getKakaoAccount() { return kakaoAccount; }

    // kakao_account 안의 내용을 담는 중첩 클래스
    public static class KakaoAccount {
        private Profile profile; // 닉네임이 들어있는 객체
        private String email;    // 카카오 이메일

        public Profile getProfile() { return profile; }
        public String getEmail() { return email; }

        // profile 안의 내용을 담는 중첩 클래스
        public static class Profile {
            private String nickname; // 카카오 닉네임
            public String getNickname() { return nickname; }
        }
    }
}
