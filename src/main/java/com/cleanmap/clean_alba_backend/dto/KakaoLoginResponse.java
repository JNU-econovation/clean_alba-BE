package com.cleanmap.clean_alba_backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KakaoLoginResponse {

    private String token;
    private String userEmail;
    private String nickname;

}