package com.cleanmap.clean_alba_backend.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 모든 요청마다 딱 한 번 실행되는 필터
// JWT가 블랙리스트에 있으면 요청을 막음
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jetUtil;
    private final JwtBlacklistUtill jwtBlacklistUtill;

    @Override
    protected  void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
        throws ServletException, IOException{

        String authHeader = request.getHeader("Authorization");

        //Authorization 헤더가 있고 "Bearer "로 시작하는 경우에만 검사
        if (authHeader != null && authHeader.startsWith("Bearer ")){
            String token = authHeader.replace("Bearer ", "");

            // 블랙리스트에 있으면 -> 401 반환하고 요청 차단
            if (jwtBlacklistUtill.isBlacklisted(token)){
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); //401
                response.getWriter().write("로그아웃된 토큰입니다. 다시 로그인해주세요.");
                return; //요청을 여기에서 막음
            }
        }
    }
}
