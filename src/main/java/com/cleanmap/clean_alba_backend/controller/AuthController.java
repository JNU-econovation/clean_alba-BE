package com.cleanmap.clean_alba_backend.controller;

import com.cleanmap.clean_alba_backend.util.JwtBlacklistUtill;
import com.cleanmap.clean_alba_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** 기존 서비스 JWT의 신원을 유지하면서 만료 시각을 갱신하는 인증 API다. */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final JwtBlacklistUtill jwtBlacklistUtill;

    // POST /auth/refresh
    // 아직 유효한 토큰을 헤더에 담아 보내면, 만료시간을 새로 찍은 토큰을 재발급(슬라이딩 갱신).
    // 만료된 뒤에는 갱신 불가 → 만료 전에 프론트가 호출해야 함.
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @RequestHeader("Authorization") String authorizationHeader) {

        String token = authorizationHeader.replace("Bearer ", "");

        // 로그아웃(블랙리스트)된 토큰은 갱신 불가
        if (jwtBlacklistUtill.isBlacklisted(token)) {
            return ResponseEntity.status(401).body("로그아웃된 토큰입니다. 다시 로그인해주세요.");
        }

        // 만료·위조된 토큰은 갱신 불가
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(401).body("유효하지 않은 토큰입니다. 다시 로그인해주세요.");
        }

        String email = jwtUtil.getEmailFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);
        String newToken = jwtUtil.reissueToken(email, role);

        Map<String, String> result = new HashMap<>();
        result.put("token", newToken);
        return ResponseEntity.ok(result);
    }
}
