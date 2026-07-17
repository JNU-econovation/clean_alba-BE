package com.cleanmap.clean_alba_backend;

import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.repository.ReviewRepository;
import com.cleanmap.clean_alba_backend.repository.WorkspaceRepository;
import com.cleanmap.clean_alba_backend.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
class ReviewCreateContractIntegrationTest {

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

    private Workspace workspace;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        workspace = workspaceRepository.saveAndFlush(new Workspace(
                "후기 계약 검증 " + UUID.randomUUID(), "광주 북구 테스트로 1", "카페", "테스트",
                new BigDecimal("35.5000000"), new BigDecimal("126.5000000")));
    }

    @AfterEach
    void tearDown() {
        reviewRepository.deleteAll(reviewRepository.findByWorkspace_WorkspaceIdAndStatus(
                workspace.getWorkspaceId(), com.cleanmap.clean_alba_backend.domain.ReviewStatus.PENDING));
        workspaceRepository.deleteById(workspace.getWorkspaceId());
    }

    @Test
    void createsPendingReviewWithRequestedContract() throws Exception {
        HttpResponse<String> response = post(workspace.getWorkspaceId(), validRequest(), userToken());

        assertEquals(201, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals(workspace.getWorkspaceId(), body.path("workspaceId").asLong());
        assertEquals("PENDING", body.path("status").asString());
        assertEquals("후기 내용", body.path("content").asString());
        assertEquals(true, body.path("createdAt").isTextual());
        assertEquals(3, body.path("coworkerCount").asInt());
        assertEquals("weekday", body.path("dayType").asString());
        assertEquals("morning", body.path("timeSlot").asString());
        assertEquals(2, body.path("violationItems").size());
        assertEquals("WEEKLY_ALLOWANCE", body.path("violationItems").get(0).asString());
        assertEquals("BREAK_TIME", body.path("violationItems").get(1).asString());
    }

    @Test
    void rejectsAnonymousReviewCreation() throws Exception {
        assertEquals(401, post(workspace.getWorkspaceId(), validRequest(), null).statusCode());
    }

    @Test
    void rejectsUnknownWorkspace() throws Exception {
        assertEquals(404, post(Long.MAX_VALUE, validRequest(), userToken()).statusCode());
    }

    @Test
    void rejectsMissingRequiredBoolean() throws Exception {
        String request = validRequest().replace("\"minimumWageViolation\":false,", "");
        HttpResponse<String> response = post(workspace.getWorkspaceId(), request, userToken());
        assertEquals(400, response.statusCode());
        assertEquals(true, objectMapper.readTree(response.body()).path("errors").path("minimumWageViolation").isTextual());
    }

    @Test
    void rejectsMissingBreakTimeViolation() throws Exception {
        String request = validRequest().replace("\"breakTimeViolation\":true,", "");
        HttpResponse<String> response = post(workspace.getWorkspaceId(), request, userToken());

        assertEquals(400, response.statusCode());
        assertEquals(true, objectMapper.readTree(response.body()).path("errors").path("breakTimeViolation").isTextual());
    }

    @Test
    void rejectsNegativeCoworkerCount() throws Exception {
        String request = validRequest().replace("\"coworkerCount\":3", "\"coworkerCount\":-1");
        HttpResponse<String> response = post(workspace.getWorkspaceId(), request, userToken());
        assertEquals(400, response.statusCode());
        assertEquals(true, objectMapper.readTree(response.body()).path("errors").path("coworkerCount").isTextual());
    }

    @Test
    void rejectsInvalidDayTypeAndTimeSlot() throws Exception {
        String invalidDayType = validRequest().replace("\"dayType\":\"weekday\"", "\"dayType\":\"holiday\"");
        String invalidTimeSlot = validRequest().replace("\"timeSlot\":\"morning\"", "\"timeSlot\":\"dawn\"");

        assertEquals(400, post(workspace.getWorkspaceId(), invalidDayType, userToken()).statusCode());
        assertEquals(400, post(workspace.getWorkspaceId(), invalidTimeSlot, userToken()).statusCode());
    }

    @Test
    void acceptsNullAndBlankContent() throws Exception {
        String nullContent = validRequest().replace("\"content\":\"후기 내용\"", "\"content\":null");
        String blankContent = validRequest().replace("\"content\":\"후기 내용\"", "\"content\":\"   \"");

        assertEquals(201, post(workspace.getWorkspaceId(), nullContent, userToken()).statusCode());
        assertEquals(201, post(workspace.getWorkspaceId(), blankContent, userToken()).statusCode());
    }

    private String userToken() {
        return jwtUtil.generateToken("reviewer@example.com", 42L);
    }

    private String validRequest() {
        return """
                {
                  "contractViolation":false,
                  "minimumWageViolation":false,
                  "weeklyHolidayAllowanceViolation":true,
                  "breakTimeViolation":true,
                  "wageDelayViolation":false,
                  "scheduleChangeViolation":false,
                  "substituteDemandViolation":false,
                  "overtimePayViolation":false,
                  "coworkerCount":3,
                  "content":"후기 내용",
                  "dayType":"weekday",
                  "timeSlot":"morning"
                }
                """;
    }

    private HttpResponse<String> post(Long workspaceId, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/workspaces/" + workspaceId + "/reviews"))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
