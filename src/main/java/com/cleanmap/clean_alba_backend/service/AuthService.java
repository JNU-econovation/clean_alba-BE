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

import java.util.List;

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
        // 필수는 카카오 고유 ID뿐이다. 이메일·닉네임은 계정 동의 항목에 따라 없을 수 있다.
        if (info == null || info.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "카카오 사용자 정보를 확인할 수 없습니다.");
        }
        String email = info.getKakaoAccount() == null ? null : info.getKakaoAccount().getEmail();
        String nickname = info.getKakaoAccount() == null || info.getKakaoAccount().getProfile() == null
                ? null : info.getKakaoAccount().getProfile().getNickname();

        String token = jwtUtil.generateToken(email, info.getId());
        KakaoLoginResponse response = new KakaoLoginResponse();
        response.setToken(token);
        response.setUserEmail(email);
        response.setNickname(nickname);
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

        // 필수 식별자는 kakaoId다. 카카오 계정이 이메일 제공에 동의하지 않으면
        // 토큰의 sub(email)가 비어 있을 수 있으므로 email은 선택 정보로 취급한다.
        Long kakaoId = jwtUtil.getKakaoIdFromToken(token);
        if (kakaoId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰 사용자 정보가 없습니다.");
        }
        String email = jwtUtil.getEmailFromToken(token);
        return new AuthenticatedUser(email, jwtUtil.roleFor(kakaoId), token, kakaoId);
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
        // 신규 저장용 작성자 키. 이메일 동의 여부가 나중에 바뀌어도 변하지 않도록
        // 항상 kakaoId 기반으로 고정한다. (영구 설계)
        public String authorKey() {
            return "kakao:" + kakaoId;
        }

        // 조회·소유권 비교용 후보 키. kakao 키 도입 이전에 email로 저장된
        // 기존 리뷰 데이터와의 호환을 위해 email도 후보로 허용한다.
        public List<String> authorKeyCandidates() {
            return email == null || email.isBlank()
                    ? List.of(authorKey())
                    : List.of(authorKey(), email);
        }
    }
}
