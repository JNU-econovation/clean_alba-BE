package com.cleanmap.clean_alba_backend.util;

import com.cleanmap.clean_alba_backend.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 모든 요청마다 딱 한 번 실행되는 필터
// JWT가 블랙리스트에 있으면 요청을 막음
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final AuthService authService;

    @Override
    protected  void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
        throws ServletException, IOException{

        if (!"OPTIONS".equals(request.getMethod()) && isProtected(request)) {
            try {
                if (requiresAdmin(request)) {
                    authService.requireAdmin(request.getHeader("Authorization"));
                } else {
                    authService.authenticate(request.getHeader("Authorization"));
                }
            } catch (ResponseStatusException exception) {
                response.setStatus(exception.getStatusCode().value());
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write(exception.getReason());
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isProtected(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        return path.startsWith("/admin/")
                || path.startsWith("/users/")
                || "POST".equals(method) && path.equals("/workspaces")
                || "POST".equals(method) && path.equals("/workspaces/resolve")
                || "POST".equals(method) && path.matches("/workspaces/[^/]+/reviews")
                || "POST".equals(method) && path.matches("/workspaces/[^/]+/clean-score/recalculate")
                || "POST".equals(method) && path.matches("/reviews/[^/]+/attachments")
                || "POST".equals(method) && path.equals("/reviews/purify-preview")
                || "POST".equals(method) && (path.equals("/auth/logout") || path.equals("/auth/refresh"))
                || path.equals("/api/kakao/logout")
                || path.equals("/api/kakao/me");
    }

    private boolean requiresAdmin(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/admin/")
                || "POST".equals(request.getMethod()) && path.equals("/workspaces")
                || path.matches("/workspaces/[^/]+/clean-score/recalculate");
    }
}
