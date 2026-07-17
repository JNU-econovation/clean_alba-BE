package com.cleanmap.clean_alba_backend;

import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.dto.ReviewCreateRequest;
import com.cleanmap.clean_alba_backend.repository.ReviewRepository;
import com.cleanmap.clean_alba_backend.repository.WorkspaceRepository;
import com.cleanmap.clean_alba_backend.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 작성자 키 안정성 회귀 테스트.
 * 신규 리뷰는 항상 kakao:{kakaoId} 키로 저장되므로, 같은 카카오 사용자가
 * 나중에 이메일 제공에 동의(또는 철회)해도 이전 리뷰의 조회와 첨부 소유권이 유지되어야 한다.
 * kakao 키 도입 이전에 email로 저장된 레거시 리뷰도 계속 조회·소유되어야 한다.
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
class AuthorKeyStabilityIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String REVIEW_JSON = """
            {
              "contractViolation": false,
              "minimumWageViolation": false,
              "weeklyAllowanceViolation": false,
              "breakTimeViolation": false,
              "wageDelayViolation": false,
              "scheduleChangeViolation": false,
              "substituteCoercionViolation": false,
              "overtimePayViolation": false
            }
            """;

    @Test
    void authorIdentitySurvivesEmailConsentChange() throws Exception {
        // Given: 이메일 제공에 동의하지 않은 카카오 사용자(kakaoId 5501)가 리뷰를 작성한다
        Workspace workspace = saveWorkspace("작성자키 안정성 검증 카페");
        String tokenWithoutEmail = jwtUtil.generateToken(null, 5501L);
        HttpResponse<String> created = request(
                "POST", "/workspaces/" + workspace.getWorkspaceId() + "/reviews",
                REVIEW_JSON, tokenWithoutEmail);
        assertEquals(201, created.statusCode());
        long reviewId = objectMapper.readTree(created.body()).path("reviewId").asLong();

        // Then: 저장된 작성자 키는 이메일과 무관한 안정 키다
        Review stored = reviewRepository.findById(reviewId).orElseThrow();
        assertEquals("kakao:5501", stored.getAuthorEmail());

        // When: 이메일 미동의 상태에서 본인 리뷰 조회·첨부를 하면
        HttpResponse<String> mineBefore = request("GET", "/users/me/reviews", null, tokenWithoutEmail);
        HttpResponse<String> attachBefore = multipart(
                "/reviews/" + reviewId + "/attachments", "proof1.pdf", tokenWithoutEmail);

        // Then: 조회와 소유권 확인이 성공한다
        assertEquals(200, mineBefore.statusCode());
        assertTrue(mineBefore.body().contains("\"reviewId\":" + reviewId));
        assertEquals(201, attachBefore.statusCode());

        // When: 같은 사용자가 이후 이메일 제공에 동의해 email이 포함된 토큰을 받으면
        String tokenWithEmail = jwtUtil.generateToken("consented@example.com", 5501L);
        HttpResponse<String> mineAfter = request("GET", "/users/me/reviews", null, tokenWithEmail);
        HttpResponse<String> attachAfter = multipart(
                "/reviews/" + reviewId + "/attachments", "proof2.pdf", tokenWithEmail);

        // Then: 동의 이전에 작성한 리뷰의 조회와 첨부 소유권이 그대로 유지된다
        assertEquals(200, mineAfter.statusCode());
        assertTrue(mineAfter.body().contains("\"reviewId\":" + reviewId));
        assertEquals(201, attachAfter.statusCode());

        // When: 다른 카카오 사용자가 같은 리뷰에 첨부를 시도하면
        String strangerToken = jwtUtil.generateToken(null, 5502L);
        HttpResponse<String> stranger = multipart(
                "/reviews/" + reviewId + "/attachments", "proof3.pdf", strangerToken);

        // Then: 소유권 검사가 여전히 타인을 차단한다
        assertEquals(403, stranger.statusCode());
    }

    @Test
    void legacyEmailAuthoredReviewsRemainAccessible() throws Exception {
        // Given: kakao 키 도입 이전에 email 키로 저장된 레거시 리뷰가 있다
        Workspace workspace = saveWorkspace("레거시 이메일 키 검증 카페");
        ReviewCreateRequest request = new ReviewCreateRequest(
                false, false, false, false, false, false, false, false, null, null);
        Review legacy = reviewRepository.saveAndFlush(
                new Review(workspace, request, "legacy@example.com"));

        // When: 같은 이메일의 사용자가 kakaoId가 포함된 새 토큰으로 조회·첨부하면
        String token = jwtUtil.generateToken("legacy@example.com", 5503L);
        HttpResponse<String> mine = request("GET", "/users/me/reviews", null, token);
        HttpResponse<String> attach = multipart(
                "/reviews/" + legacy.getReviewId() + "/attachments", "proof.pdf", token);

        // Then: 레거시 email 키 데이터도 계속 조회되고 소유권이 인정된다
        assertEquals(200, mine.statusCode());
        assertTrue(mine.body().contains("\"reviewId\":" + legacy.getReviewId()));
        assertEquals(201, attach.statusCode());
    }

    private Workspace saveWorkspace(String name) {
        return workspaceRepository.saveAndFlush(new Workspace(
                name, "광주광역시 북구 테스트로 1", "카페", "상대",
                new BigDecimal("35.5000000"), new BigDecimal("126.5000000")));
    }

    private HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> multipart(String path, String fileName, String token) throws Exception {
        String boundary = "AuthorKeyBoundary";
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n"
                + "%PDF-1.4\r\n--" + boundary + "--\r\n";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
