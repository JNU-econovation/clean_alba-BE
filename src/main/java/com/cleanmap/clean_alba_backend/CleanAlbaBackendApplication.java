package com.cleanmap.clean_alba_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 컴포넌트 탐색, 자동 설정과 내장 웹 서버를 시작하는 애플리케이션 진입점이다. */
@SpringBootApplication
public class CleanAlbaBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CleanAlbaBackendApplication.class, args);
    }

}
