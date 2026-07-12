package com.cleanmap.clean_alba_backend.service;

import com.cleanmap.clean_alba_backend.dto.KakaoLoginResponse;
import com.cleanmap.clean_alba_backend.dto.KakaoUserInfoDto;
import com.cleanmap.clean_alba_backend.util.JwtBlacklistUtill;
import com.cleanmap.clean_alba_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoService kakaoService;
    private final JwtUtil jwtUtil;
    private final JwtBlacklistUtill jwtBlacklistUtill;

    public KakaoLoginResponse kakaoLogin(String code) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "카카오 인가 코드가 필요합니다.");
        }

        KakaoUserInfoDto info;
        try {
            String kakaoAccessToken = kakaoService.getAccessToken(code.trim());
            info = kakaoService.getUserInfo(kakaoAccessToken);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "카카오 로그인 요청에 실패했습니다.");
        }
        if (info == null || info.getId() == null || info.getKakaoAccount() == null
                || info.getKakaoAccount().getProfile() == null
                || info.getKakaoAccount().getEmail() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "카카오 사용자 정보를 확인할 수 없습니다.");
        }

        String token = jwtUtil.generateToken(info.getKakaoAccount().getEmail(), info.getId());
        KakaoLoginResponse response = new KakaoLoginResponse();
        response.setToken(token);
        response.setUserEmail(info.getKakaoAccount().getEmail());
        response.setNickname(info.getKakaoAccount().getProfile().getNickname());
        response.setRole(jwtUtil.getRoleFromToken(token));
        return response;
    }

    public AuthenticatedUser authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer 토큰이 필요합니다.");
        }

        String token = authorizationHeader.substring(7);
        if (token.isBlank() || token.contains(" ") || jwtBlacklistUtill.isBlacklisted(token)
                || !jwtUtil.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
        }

        String email = jwtUtil.getEmailFromToken(token);
        Long kakaoId = jwtUtil.getKakaoIdFromToken(token);
        String role = kakaoId == null ? jwtUtil.getRoleFromToken(token) : jwtUtil.roleFor(kakaoId);
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰 사용자 정보가 없습니다.");
        }
        return new AuthenticatedUser(email, role, token, kakaoId);
    }

    public AuthenticatedUser requireAdmin(String authorizationHeader) {
        AuthenticatedUser user = authenticate(authorizationHeader);
        if (!"ADMIN".equals(user.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
        return user;
    }

    public void logout(String authorizationHeader) {
        AuthenticatedUser user = authenticate(authorizationHeader);
        jwtBlacklistUtill.addToBlacklist(user.token());
    }

    public synchronized String refresh(String authorizationHeader) {
        AuthenticatedUser user = authenticate(authorizationHeader);
        if (user.kakaoId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "다시 로그인해주세요.");
        }
        String newToken = jwtUtil.reissueToken(user.email(), user.kakaoId());
        jwtBlacklistUtill.addToBlacklist(user.token());
        return newToken;
    }

    public record AuthenticatedUser(String email, String role, String token, Long kakaoId) {
    }
}
