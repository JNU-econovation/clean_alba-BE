package com.cleanmap.clean_alba_backend.service;

import com.cleanmap.clean_alba_backend.util.JwtBlacklistUtill;
import com.cleanmap.clean_alba_backend.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 카카오 로그인 토큰 검증 규칙: 필수 식별자는 kakaoId이고 email은 선택 정보다.
 * (카카오 계정이 이메일 제공에 동의하지 않으면 sub 클레임 없이 토큰이 발급된다.)
 */
class AuthServiceTest {

    private static final String SECRET =
            "0123456789012345678901234567890123456789012345678901234567890123";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 86_400_000L, "1");
    private final AuthService authService =
            new AuthService(null, jwtUtil, new JwtBlacklistUtill());

    @Test
    void authenticatesTokenWithKakaoIdAndEmail() {
        // Given: 이메일 동의까지 마친 일반 사용자 토큰
        String token = jwtUtil.generateToken("user@example.com", 2L);

        // When: 인증하면
        AuthService.AuthenticatedUser user = authService.authenticate("Bearer " + token);

        // Then: email·kakaoId가 모두 채워지고, 작성자 키는 이메일 동의 여부와 무관하게
        //       kakaoId로 고정되며 email은 레거시 조회 후보로만 쓰인다
        assertEquals("user@example.com", user.email());
        assertEquals(2L, user.kakaoId());
        assertEquals("USER", user.role());
        assertEquals("kakao:2", user.authorKey());
        assertEquals(List.of("kakao:2", "user@example.com"), user.authorKeyCandidates());
    }

    @Test
    void authenticatesTokenWithoutEmail() {
        // Given: 이메일 제공에 동의하지 않은 카카오 계정의 토큰(sub 없음)
        String token = jwtUtil.generateToken(null, 7L);

        // When: 인증하면
        AuthService.AuthenticatedUser user = authService.authenticate("Bearer " + token);

        // Then: kakaoId만으로 인증에 성공하고 작성자 키는 kakaoId 기반이다
        assertNull(user.email());
        assertEquals(7L, user.kakaoId());
        assertEquals("kakao:7", user.authorKey());
        assertEquals(List.of("kakao:7"), user.authorKeyCandidates());
    }

    @Test
    void rejectsTokenWithoutKakaoIdEvenIfEmailExists() {
        // Given: kakaoId 클레임이 없는 토큰(email 존재 여부와 무관하게 무효)
        String token = jwtUtil.generateToken("user@example.com", null);

        // When: 인증하면
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.authenticate("Bearer " + token));

        // Then: 카카오 필수 식별자가 없어 401이다
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("토큰 사용자 정보가 없습니다.", exception.getReason());
    }

    @Test
    void rejectsMalformedToken() {
        // Given/When: JWT 형식이 아닌 문자열로 인증하면
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.authenticate("Bearer not-a-jwt"));

        // Then: 서명 검증 단계에서 401이다
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("유효하지 않은 토큰입니다.", exception.getReason());
    }

    @Test
    void rejectsExpiredToken() {
        // Given: 이미 만료된 토큰(만료시간을 음수로 설정해 발급)
        JwtUtil expiredIssuer = new JwtUtil(SECRET, -1_000L, "1");
        String token = expiredIssuer.generateToken("user@example.com", 2L);

        // When: 인증하면
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.authenticate("Bearer " + token));

        // Then: 만료 검증에서 401이다
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("유효하지 않은 토큰입니다.", exception.getReason());
    }
}
