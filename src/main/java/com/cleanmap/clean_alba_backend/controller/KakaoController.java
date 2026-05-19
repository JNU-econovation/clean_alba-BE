package com.cleanmap.clean_alba_backend.controller;

import com.cleanmap.clean_alba_backend.dto.KakaoLoginResponse;
import com.cleanmap.clean_alba_backend.dto.KakaoUserInfoDto;
import com.cleanmap.clean_alba_backend.service.KakaoService;
import com.cleanmap.clean_alba_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kakao")
@RequiredArgsConstructor
public class KakaoController {

    private final KakaoService kakaoService;
    private final JwtUtil jwtUtil;

    @GetMapping("/callback")
    public KakaoLoginResponse kakaoCallback(@RequestParam("code") String code) {
        System.out.println("카카오가 보내준 인가 코드: " + code);

        String kakaoAccessToken = kakaoService.getAccessToken(code);
        KakaoUserInfoDto kakaoRawInfo = kakaoService.getUserInfo(kakaoAccessToken);

        String email = kakaoRawInfo.getKakaoAccount().getEmail();
        String nickname = kakaoRawInfo.getKakaoAccount().getProfile().getNickname();
        String jwtToken = jwtUtil.generateToken(email);

        KakaoLoginResponse finalResponse = new KakaoLoginResponse();
        finalResponse.setToken(jwtToken);
        finalResponse.setUserEmail(email);
        finalResponse.setNickname(nickname);

        System.out.println("백엔드에서 재포장 완료: " + finalResponse.getNickname());

        return finalResponse;
    }
}
