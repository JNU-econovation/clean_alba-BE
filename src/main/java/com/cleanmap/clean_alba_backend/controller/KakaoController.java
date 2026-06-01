package com.cleanmap.clean_alba_backend.controller;

import com.cleanmap.clean_alba_backend.dto.KakaoLoginResponse;
import com.cleanmap.clean_alba_backend.dto.KakaoUserInfoDto;
import com.cleanmap.clean_alba_backend.service.KakaoService;
import com.cleanmap.clean_alba_backend.util.JwtBlacklistUtill;
import com.cleanmap.clean_alba_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

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
    // [전체 흐름]
    // 프론트(5173)에서 카카오 로그인 버튼 클릭
    // -> 카카오 로그인 페이지 이동
    // -> 사용자가 카카오 로그인 이동
    // -> 카카오가 프론트의 redirect-url로 인가 코드를 보냄
    // -> 프론트가 그 인가 코드를 꺼내서 이 api(/api/kakao/callback?code=xxx)를 호출
    // -> 백엔드가 카카오와 통신해서 jwt(토큰)를 만들어 프론트에 반환
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
        Long kakaoId = kakaoRawInfo.getId();
        String jwtToken = jwtUtil.generateToken(email, kakaoId);

        // 5단계: 프론트에게 돌려줄 응답 객체를 만들어서 값을 담음
        //        → KakaoLoginResponse 구조는 dto/KakaoLoginResponse.java 참고
        KakaoLoginResponse finalResponse = new KakaoLoginResponse();
        finalResponse.setToken(jwtToken);       // 우리 서비스 JWT
        finalResponse.setUserEmail(email);      // 카카오 이메일
        finalResponse.setNickname(nickname);    // 카카오 닉네임

        System.out.println("백엔드에서 재포장 완료: " + finalResponse.getNickname());

        // 관리자의 카카오 고유 id확인용
        System.out.println("카카오 고유 id: " + kakaoRawInfo.getId());

        // 최종 응답: { "token": "eyJ...", "userEmail": "...", "nickname": "..." }
        return finalResponse;
    }

    // JwtBlacklistUtil도 주입받아야 하니까 필드에 추가
    private final JwtBlacklistUtill jwtBlacklistUtill;

    // Authorization 헤더에서 토큰만 꺼내는 메서드
    private String extractToken(String authorizatonHeader){
        return authorizatonHeader.replace("Bearer ","");
    }

    // 토큰 유효성 검사 + 에러 응답 처리
    // 유효 -> null, 유효x -> 에러 responseentity 반환
    private ResponseEntity<String> validateOrError(String token){
        if (!jwtUtil.validateToken(token)){
            return ResponseEntity.status(401).body("유효하지 않은 토큰입니다.");
        }
        return null;
    }

    // POST /api/kakao/logout
    // 프론트가 헤더에 "Authorization: Bearer eyJ..." 형태로 JWT를 담아서 요청
    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @RequestHeader("Authorization") String authorizationHeader) {

        // "Bearer eyJ..."에서 "Bearer " 부분을 잘라내고 토큰만 꺼냄
        String token = extractToken(authorizationHeader);

        ResponseEntity<String> error = validateOrError(token);
        if (error != null) return error;

        // 블랙리스트에 추가 -> 이 토큰은 이제 못 씀
        jwtBlacklistUtill.addToBlacklist(token);
        System.out.println("로그아웃 완료: " + jwtUtil.getEmailFromToken(token));

        return ResponseEntity.ok("로그아웃 되었습니다.");
    }

    // GET /api/kakao/me
    // 프론트가 헤더에 jwt 담아서 요청하면 -> 유저 정보 + role 반환
    @GetMapping("/me")
    public ResponseEntity<?> getMyInfo(
            @RequestHeader("Authorization") String authorizationHeader){

        String token = extractToken(authorizationHeader);

        ResponseEntity<String> error = validateOrError(token);
        if (error != null) return error;

        Map<String, String> result = new HashMap<>();
        result.put("email", jwtUtil.getEmailFromToken(token));
        result.put("role", jwtUtil.getRoleFromToken(token));

        return ResponseEntity.ok(result);
    }
}