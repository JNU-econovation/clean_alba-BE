package com.cleanmap.clean_alba_backend;

import com.cleanmap.clean_alba_backend.service.PurifyService;
import com.cleanmap.clean_alba_backend.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * POST /reviews/purity-preview 인증 연동 검증.
 * 유효한 카카오 JWT(이메일 미제공 계정 포함)가 필터·컨트롤러 인증을 모두 통과해
 * PurifyService까지 도달하는지 확인한다. Solar 호출은 목으로 대체한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
                "admin.ids=1",
                "kakao.client-id=test-client",
                "kakao.client-secret=test-secret",
                "upstage.api-key=test-key"
        }
)
class PurityPreviewAuthIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PurifyService purifyService;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void validKakaoTokenReachesPurifyController() throws Exception {
        // Given: 이메일 동의를 마친 사용자 토큰과 목 순화 결과
        when(purifyService.purify(anyString()))
                .thenReturn(objectMapper.readTree("{\"risk_assessment\":{\"risk_level\":\"SAFE\"}}"));
        String token = jwtUtil.generateToken("worker@example.com", 2L);

        // When: 유효한 토큰으로 순화 미리보기를 호출하면
        HttpResponse<String> response = post(token);

        // Then: 인증을 통과해 컨트롤러가 순화 결과를 반환한다
        assertEquals(200, response.statusCode());
        assertEquals("SAFE", objectMapper.readTree(response.body())
                .path("risk_assessment").path("risk_level").asString());
    }

    @Test
    void kakaoTokenWithoutEmailAlsoReachesController() throws Exception {
        // Given: 이메일 제공에 동의하지 않은 카카오 계정의 토큰(sub 클레임 없음)
        when(purifyService.purify(anyString()))
                .thenReturn(objectMapper.readTree("{\"risk_assessment\":{\"risk_level\":\"LOW\"}}"));
        String token = jwtUtil.generateToken(null, 9L);

        // When: 해당 토큰으로 순화 미리보기를 호출하면
        HttpResponse<String> response = post(token);

        // Then: kakaoId만으로 인증이 성공한다 ("토큰 사용자 정보가 없습니다" 401 회귀 방지)
        assertEquals(200, response.statusCode());
    }

    @Test
    void tokenWithoutKakaoIdIsRejected() throws Exception {
        // Given: kakaoId 클레임이 없는 토큰
        String token = jwtUtil.generateToken("worker@example.com", null);

        // When/Then: 필수 식별자가 없어 401이다
        assertEquals(401, post(token).statusCode());
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        // Given/When/Then: 토큰 없이 호출하면 401이다
        assertEquals(401, post(null).statusCode());
    }

    private HttpResponse<String> post(String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/reviews/purity-preview"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"reviewText\":\"사장이 월급을 늦게 줬어요\"}"));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
