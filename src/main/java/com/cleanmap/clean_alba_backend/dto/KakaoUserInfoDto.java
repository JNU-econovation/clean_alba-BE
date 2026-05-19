package com.cleanmap.clean_alba_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class KakaoUserInfoDto {
    private Long id;

    @JsonProperty("kakao_account")
    private KakaoAccount kakaoAccount;

    public Long getId() { return id; }
    public KakaoAccount getKakaoAccount() { return kakaoAccount; }

    public static class KakaoAccount {
        private Profile profile;
        private String email;

        public Profile getProfile() { return profile; }
        public String getEmail() { return email; }

        public static class Profile {
            private String nickname;
            public String getNickname() { return nickname; }
        }
    }
}