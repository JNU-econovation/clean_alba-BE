package com.cleanmap.clean_alba_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// @Configuration: 스프링 설정 클래스임을 선언
// WebMvcConfigurer: 스프링 MVC 설정을 커스텀할 수 있게 해주는 인터페이스
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")                          // 모든 API 경로에 적용
                .allowedOrigins("http://localhost:5173")    // 프론트 주소 허용
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")  // 허용할 HTTP 메서드
                .allowedHeaders("*")                        // 모든 헤더 허용
                .allowCredentials(true);                    // 쿠키/인증 정보 포함 허용
    }
}
