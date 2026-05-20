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

// @RestController: 이 클래스가 API 요청을 받는 컨트롤러임을 선언
// @RequestMapping: 이 컨트롤러의 모든 API 주소는 /api/kakao 로 시작
// @RequiredArgsConstructor: final로 선언된 필드를 자동으로 생성자 주입해줌 (= new 안 해도 됨)
@RestController
@RequestMapping("/api/kakao")
@RequiredArgsConstructor
public class KakaoController {

    // KakaoService: 카카오 API와 실제로 통신하는 로직이 담긴 클래스 (service/KakaoService.java)
    private final KakaoService kakaoService;

    // JwtUtil: 우리 서비스의 JWT 토큰을 발급해주는 유틸 클래스 (util/JwtUtil.java)
    private final JwtUtil jwtUtil;

    // GET /api/kakao/callback?code=xxxx 요청을 처리하는 메서드
    // 카카오 로그인 후 카카오가 프론트에 인가 코드를 주면,
    // 프론트가 그 코드를 들고 이 API를 호출함
    @GetMapping("/callback")
    public KakaoLoginResponse kakaoCallback(@RequestParam("code") String code) {
        System.out.println("카카오가 보내준 인가 코드: " + code);

        // 1단계: 카카오에게 인가 코드를 주고 카카오 액세스 토큰을 받아옴
        //        → KakaoService.getAccessToken() 호출
        String kakaoAccessToken = kakaoService.getAccessToken(code);

        // 2단계: 카카오 액세스 토큰으로 카카오에게 유저 정보(이메일, 닉네임)를 요청
        //        → KakaoService.getUserInfo() 호출
        //        → 결과는 KakaoUserInfoDto 형태로 반환됨 (dto/KakaoUserInfoDto.java)
        KakaoUserInfoDto kakaoRawInfo = kakaoService.getUserInfo(kakaoAccessToken);

        // 3단계: 받아온 유저 정보에서 이메일과 닉네임을 꺼냄
        String email = kakaoRawInfo.getKakaoAccount().getEmail();
        String nickname = kakaoRawInfo.getKakaoAccount().getProfile().getNickname();

        // 4단계: 이메일을 기반으로 우리 서비스의 JWT 토큰을 직접 발급
        //        카카오 토큰은 프론트에 주지 않고, 우리가 만든 JWT만 전달함
        //        → JwtUtil.generateToken() 호출 (util/JwtUtil.java)
        String jwtToken = jwtUtil.generateToken(email);

        // 5단계: 프론트에게 돌려줄 응답 객체를 만들어서 값을 담음
        //        → KakaoLoginResponse 구조는 dto/KakaoLoginResponse.java 참고
        KakaoLoginResponse finalResponse = new KakaoLoginResponse();
        finalResponse.setToken(jwtToken);       // 우리 서비스 JWT
        finalResponse.setUserEmail(email);      // 카카오 이메일
        finalResponse.setNickname(nickname);    // 카카오 닉네임

        System.out.println("백엔드에서 재포장 완료: " + finalResponse.getNickname());

        // 최종 응답: { "token": "eyJ...", "userEmail": "...", "nickname": "..." }
        return finalResponse;
    }
}
