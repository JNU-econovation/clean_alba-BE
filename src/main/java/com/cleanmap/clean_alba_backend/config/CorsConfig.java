package com.cleanmap.clean_alba_backend.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

// CORS를 MVC(DispatcherServlet) 단계가 아니라 서블릿 필터 단계에서 처리한다.
// JwtAuthFilter보다 먼저 실행되므로, 인증 실패(401/403)로 요청이 중단돼도 응답에 CORS 헤더가 붙어
// 프론트엔드(vercel/localhost)가 상태 코드를 정상적으로 읽을 수 있다.
@Configuration
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "https://cleanalb-map-fe.vercel.app"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> registration =
                new FilterRegistrationBean<>(new CorsFilter(source));
        // JwtAuthFilter(기본 순서)보다 먼저 실행되어야 인증 실패 응답에도 CORS 헤더가 붙는다.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
