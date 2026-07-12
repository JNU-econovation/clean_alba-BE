package com.cleanmap.clean_alba_backend.controller;

import com.cleanmap.clean_alba_backend.dto.KakaoAuthCodeRequest;
import com.cleanmap.clean_alba_backend.dto.KakaoLoginResponse;
import com.cleanmap.clean_alba_backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** 기존 서비스 JWT의 신원을 유지하면서 만료 시각을 갱신하는 인증 API다. */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/kakao/callback")
    public KakaoLoginResponse kakaoCallback(@RequestBody KakaoAuthCodeRequest request) {
        return authService.kakaoLogin(request.code());
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        authService.logout(authorizationHeader);
        return ResponseEntity.ok("로그아웃 되었습니다.");
    }

    // POST /auth/refresh
    // 아직 유효한 토큰을 헤더에 담아 보내면, 만료시간을 새로 찍은 토큰을 재발급(슬라이딩 갱신).
    // 만료된 뒤에는 갱신 불가 → 만료 전에 프론트가 호출해야 함.
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @RequestHeader("Authorization") String authorizationHeader) {

        Map<String, String> result = new HashMap<>();
        result.put("token", authService.refresh(authorizationHeader));
        return ResponseEntity.ok(result);
    }
}
