package com.cleanmap.clean_alba_backend.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

    // JWT 토큰을 발급하는 메서드
    // email을 토큰 안에 담아두면, 나중에 토큰만 보고도 누구인지 알 수 있음
    // KakaoController에서 호출됨
    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)                                                   // 토큰 주인 (이메일)
                .issuedAt(new Date())                                             // 발급 시각
                .expiration(new Date(System.currentTimeMillis() + expirationMs)) // 만료 시각
                .signWith(secretKey)                                              // 비밀 키로 서명
                .compact();                                                       // 문자열 형태로 변환
    }
}
