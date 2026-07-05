package com.cleanmap.clean_alba_backend.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecureRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

// @Component: 스프링이 이 클래스를 자동으로 관리하도록 등록
//             KakaoController에서 JwtUtil을 주입받아 쓸 수 있는 이유
@Component
public class JwtUtil {

    // JWT 서명에 사용할 비밀 키 (application.yml의 jwt.secret 값)
    private final SecretKey secretKey;

    // 토큰 만료 시간 (application.yml의 jwt.expiration-ms 값, 현재 86400000ms = 24시간)
    private final long expirationMs;

    // 생성자: 애플리케이션 시작 시 application.yml에서 값을 읽어와 SecretKey 객체를 만들어둠
    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes()); // 문자열 → 암호화 키 변환
        this.expirationMs = expirationMs;
    }

    // application.yml의 admin.emails 값 받음
    @Value("${admin.ids}")
    private String adminIds;

    // JWT 토큰을 발급하는 메서드
    // email을 토큰 안에 담아두면, 나중에 토큰만 보고도 누구인지 알 수 있음
    // KakaoController에서 호출됨
    public String generateToken(String email, Long kakaoId) {

        // 관지자 이메일 목록에 있으면 ADMIN, 없으면 USER
        String role = adminIds.contains(String.valueOf(kakaoId)) ? "ADMIN" : "USER";

        return Jwts.builder()
                .subject(email)                                                   // 토큰 주인 (이메일) - 나중에 꺼내서 누구인지 확인하는 용도
                .claim("role",role)
                .issuedAt(new Date())                                             // 발급 시각
                .expiration(new Date(System.currentTimeMillis() + expirationMs)) // 만료 시각
                .signWith(secretKey)                                              // 비밀 키로 서명 -> 토큰의 위조됐는지 검증용
                .compact();                                                       // 문자열 형태로 변환
    }

    // 기존 토큰의 email·role을 그대로 유지한 채 만료시간만 새로 찍어 재발급 (슬라이딩 갱신)
    // 토큰에 kakaoId가 없어 generateToken을 못 쓰므로, 이미 검증된 role을 넘겨받아 재발급한다.
    public String reissueToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    //토큰에서 role을 꺼냄
    public String getRoleFromToken(String token){
        return (String) Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role"); // claim에서 role 꺼내기
    }

    // 토큰에서 이메일을 꺼내는 메서드 (로그아웃 시 누구인지 확인용)
    public String getEmailFromToken(String token){
        return Jwts.parser()
                .verifyWith(secretKey)    // 비밀키로 서명 검증
                .build()
                .parseSignedClaims(token) // 토큰 파싱
                .getPayload()
                .getSubject();            // subject에 넣었던 이메일 꺼내기
    }

    // 토큰이 유효한지 검사하는 메서드 (만료/위조 시 false)
    public boolean validateToken(String token){
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true; // 문제없으면 유효
        } catch (Exception e){
            return false; // 만료/위조/형식오류면 무효
        }
    }
}
