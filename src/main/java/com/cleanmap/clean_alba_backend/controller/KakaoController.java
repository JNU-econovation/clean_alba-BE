package com.cleanmap.clean_alba_backend.controller;

import com.cleanmap.clean_alba_backend.dto.KakaoUserInfoDto;
import com.cleanmap.clean_alba_backend.service.KakaoService;
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

    @GetMapping("/callback")
    public KakaoUserInfoDto kakaoCallback(@RequestParam("code") String code) {
        System.out.println("카카오가 보내준 인가 코드: " + code);

        //토큰 받아오기
        String accessToken = kakaoService.getAccessToken(code);
        System.out.println("controller -> 엑세스 토큰 받아옴" + accessToken);

        //토큰 넘기기
        KakaoUserInfoDto userInfo = kakaoService.getUserInfo(accessToken);
        System.out.println("유저 정보 획득, 이 유저의 이름은 = " + userInfo.getKakaoAccount().getProfile().getNickname());

        //화면에 유저정보 띄우기
        return userInfo;
    }
}
