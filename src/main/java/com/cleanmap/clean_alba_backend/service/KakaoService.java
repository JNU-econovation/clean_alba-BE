package com.cleanmap.clean_alba_backend.service;

import com.cleanmap.clean_alba_backend.dto.KakaoTokenDto;
import com.cleanmap.clean_alba_backend.dto.KakaoUserInfoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

// @Service: 이 클래스가 비즈니스 로직을 담당하는 서비스임을 선언
//           카카오 API와 실제로 HTTP 통신하는 코드가 여기에 있음
@Service
public class KakaoService {

    // application.yml의 kakao.client-id 값을 자동으로 주입받음
    @Value("${kakao.client-id}")
    private String clientId;

    // application.yml의 kakao.client-secret 값을 자동으로 주입받음
    @Value("${kakao.client-secret}")
    private String clientSecret;

    // application.yml의 kakao.redirect-url 값을 자동으로 주입받음
    @Value("${kakao.redirect-url}")
    private String redirectUri;

    // [1단계] 카카오에게 인가 코드를 주고 액세스 토큰을 받아오는 메서드
    // 카카오 공식 문서: https://kauth.kakao.com/oauth/token 에 POST 요청
    public String getAccessToken(String code) {

        // RestTemplate: 외부 서버에 HTTP 요청을 보내는 스프링 내장 클래스
        RestTemplate rt = new RestTemplate();

        // 요청 헤더 설정 (카카오가 요구하는 형식)
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        // 요청 바디에 담을 파라미터 (카카오 토큰 발급에 필요한 값들)
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");  // 고정값
        params.add("client_id", clientId);               // 카카오 앱 키 (application.yml에서 읽어옴)
        params.add("client_secret", clientSecret);       // 카카오 클라이언트 시크릿 (보안용, 백엔드에서만 사용)
        params.add("redirect_uri", redirectUri);         // 카카오 디벨로퍼스에 등록한 리다이렉트 주소
        params.add("code", code);                        // 프론트에서 넘겨준 인가 코드

        // 헤더 + 바디를 하나로 묶어서 요청 객체 생성
        HttpEntity<MultiValueMap<String, String>> kakaoTokenRequest = new HttpEntity<>(params, headers);

        // 카카오 토큰 발급 서버에 POST 요청 전송
        // 응답은 KakaoTokenDto 형태로 자동 변환됨 (dto/KakaoTokenDto.java)
        ResponseEntity<KakaoTokenDto> response = rt.exchange(
                "https://kauth.kakao.com/oauth/token",
                HttpMethod.POST,
                kakaoTokenRequest,
                KakaoTokenDto.class
        );

        // 응답에서 액세스 토큰만 꺼내서 반환
        return response.getBody().getAccessToken();
    }

    // [2단계] 카카오 액세스 토큰으로 유저 정보(이메일, 닉네임)를 가져오는 메서드
    // 카카오 공식 문서: https://kapi.kakao.com/v2/user/me 에 POST 요청
    public KakaoUserInfoDto getUserInfo(String accessToken) {
        RestTemplate rt = new RestTemplate();

        // 요청 헤더에 액세스 토큰을 담아야 카카오가 누구인지 확인해줌
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken); // "Bearer " 뒤에 토큰 붙이는 형식
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        // 바디 없이 헤더만 담아서 요청 객체 생성
        HttpEntity<MultiValueMap<String, String>> kakaoProfileRequest = new HttpEntity<>(headers);

        // 카카오 유저 정보 서버에 POST 요청 전송
        // 응답은 KakaoUserInfoDto 형태로 자동 변환됨 (dto/KakaoUserInfoDto.java)
        ResponseEntity<KakaoUserInfoDto> response = rt.exchange(
                "https://kapi.kakao.com/v2/user/me",
                HttpMethod.POST,
                kakaoProfileRequest,
                KakaoUserInfoDto.class
        );

        return response.getBody();
    }
}
