package com.cleanmap.clean_alba_backend;

import com.cleanmap.clean_alba_backend.util.JwtUtil;
import com.cleanmap.clean_alba_backend.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
class PlannedApiIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void koreanWorkspaceSeedDataRemainsSearchable() throws Exception {
        // Given: the application has loaded its Korean workspace seed data

        // When: a user searches by the Korean cafe category
        HttpResponse<String> response = request(
                "GET", "/workspaces?keyword=%EC%B9%B4%ED%8E%98", null, null);

        // Then: the UTF-8 names and categories are searchable without corruption
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("카페"));
    }

    @Test
    void authenticationPrecedesMalformedBodyParsing() throws Exception {
        // Given: an anonymous caller sends malformed JSON to a protected route

        // When: the request reaches the application
        HttpResponse<String> response = request("POST", "/workspaces/10/reviews", "{bad", null);
        HttpResponse<String> negativeIdResponse = request("POST", "/workspaces/-1/reviews", "{bad", null);
        HttpResponse<String> resolveResponse = request("POST", "/workspaces/resolve", "{bad", null);

        // Then: authentication rejects it before JSON parsing
        assertEquals(401, response.statusCode());
        assertEquals(401, negativeIdResponse.statusCode());
        assertEquals(401, resolveResponse.statusCode());
    }

    @Test
    void rejectingReviewDoesNotRecalculateWorkspaceScore() throws Exception {
        // Given: a workspace has a stored score and a pending review
        var workspace = workspaceRepository.findById(10L).orElseThrow();
        workspace.updateCleanScore(12.0);
        workspaceRepository.saveAndFlush(workspace);
        String userToken = jwtUtil.generateToken("rejected@example.com", 2L);
        String adminToken = jwtUtil.generateToken("admin@example.com", 1L);
        String reviewJson = """
                {
                  "contractViolation": false,
                  "minimumWageViolation": false,
                  "weeklyAllowanceViolation": false,
                  "breakTimeViolation": false,
                  "wageDelayViolation": false,
                  "scheduleChangeViolation": false,
                  "substituteCoercionViolation": false,
                  "overtimePayViolation": false,
                  "coworkerCount": 0,
                  "dayType": "weekday",
                  "timeSlot": "morning"
                }
                """;
        HttpResponse<String> created = request("POST", "/workspaces/10/reviews", reviewJson, userToken);
        long reviewId = objectMapper.readTree(created.body()).path("reviewId").asLong();

        // When: an administrator rejects the review
        HttpResponse<String> rejected = request(
                "PATCH", "/admin/reviews/" + reviewId + "/status", "{\"status\":\"REJECTED\"}", adminToken);

        // Then: the moderation succeeds without changing the stored score
        assertEquals(200, rejected.statusCode());
        HttpResponse<String> detail = request("GET", "/workspaces/10", null, null);
        assertEquals(12, objectMapper.readTree(detail.body()).path("cleanScore").asInt());
    }

    @Test
    void plannedReviewAndAdminApisWorkEndToEnd() throws Exception {
        // Given: an unscored seeded workspace and valid USER/ADMIN tokens
        String userToken = jwtUtil.generateToken("worker@example.com", 2L);
        String adminToken = jwtUtil.generateToken("admin@example.com", 1L);
        String reviewJson = """
                {
                  "contractViolation": false,
                  "minimumWageViolation": false,
                  "weeklyAllowanceViolation": false,
                  "breakTimeViolation": true,
                  "wageDelayViolation": false,
                  "scheduleChangeViolation": false,
                  "substituteCoercionViolation": false,
                  "overtimePayViolation": false,
                  "coworkerCount": 2,
                  "content": "휴게시간이 부족했어요.",
                  "dayType": "weekday",
                  "timeSlot": "morning"
                }
                """;

        // When: the protected review route is called without a token
        HttpResponse<String> unauthorized = request("POST", "/workspaces/10/reviews", reviewJson, null);

        // Then: authentication is required
        assertEquals(401, unauthorized.statusCode());

        // When: the user submits a valid review
        HttpResponse<String> created = request("POST", "/workspaces/10/reviews", reviewJson, userToken);

        // Then: the server owns its pending moderation state
        assertEquals(201, created.statusCode());
        JsonNode createdBody = objectMapper.readTree(created.body());
        long reviewId = createdBody.path("reviewId").asLong();
        assertEquals("PENDING", createdBody.path("status").asString());

        // When: the author attaches an allowed proof file and an unsupported file
        HttpResponse<String> attachment = multipart(
                "/reviews/" + reviewId + "/attachments", "proof.pdf", "application/pdf", "%PDF-1.4", userToken);
        HttpResponse<String> invalidAttachment = multipart(
                "/reviews/" + reviewId + "/attachments", "proof.exe", "application/octet-stream", "bad", userToken);

        // Then: only the documented attachment formats are accepted
        assertEquals(201, attachment.statusCode());
        assertEquals(reviewId, objectMapper.readTree(attachment.body()).path("reviewId").asLong());
        assertEquals(400, invalidAttachment.statusCode());

        // When: the user reads their own reviews
        HttpResponse<String> mine = request("GET", "/users/me/reviews", null, userToken);

        // Then: the submitted review is present
        assertEquals(200, mine.statusCode());
        assertTrue(mine.body().contains("\"reviewId\":" + reviewId));

        // When: a normal user opens the admin queue
        HttpResponse<String> forbidden = request("GET", "/admin/reviews?status=pending", null, userToken);

        // Then: role authorization rejects the request
        assertEquals(403, forbidden.statusCode());

        // When: an administrator reads and approves the review
        HttpResponse<String> queue = request("GET", "/admin/reviews?status=pending", null, adminToken);
        HttpResponse<String> detail = request("GET", "/admin/reviews/" + reviewId, null, adminToken);
        HttpResponse<String> approved = request(
                "PATCH", "/admin/reviews/" + reviewId + "/status", "{\"status\":\"APPROVED\"}", adminToken);

        // Then: queue, detail, and one-way moderation all succeed
        assertEquals(200, queue.statusCode());
        assertTrue(queue.body().contains("\"reviewId\":" + reviewId));
        assertEquals(200, detail.statusCode());
        assertEquals(200, approved.statusCode());
        assertEquals("APPROVED", objectMapper.readTree(approved.body()).path("status").asString());
        assertEquals(409, request(
                "PATCH", "/admin/reviews/" + reviewId + "/status", "{\"status\":\"REJECTED\"}", adminToken
        ).statusCode());

        // When: public detail/summary and admin statistics are read after approval
        HttpResponse<String> workspaceDetail = request("GET", "/workspaces/10", null, null);
        HttpResponse<String> workspaceSummary = request("GET", "/workspaces/10/summary", null, null);
        HttpResponse<String> stats = request("GET", "/admin/stats", null, adminToken);

        // Then: approved review data and recalculated score are observable
        assertEquals(200, workspaceDetail.statusCode());
        assertEquals(88, objectMapper.readTree(workspaceDetail.body()).path("cleanScore").asInt());
        assertEquals(1, objectMapper.readTree(workspaceDetail.body()).path("reviewCount").asInt());
        assertEquals(200, workspaceSummary.statusCode());
        assertEquals(1, objectMapper.readTree(workspaceSummary.body()).path("reviewCount").asInt());
        assertEquals("휴게시간이 부족했어요.",
                objectMapper.readTree(workspaceSummary.body()).path("reviewSummary").asString());
        assertEquals(200, stats.statusCode());
        assertTrue(objectMapper.readTree(stats.body()).path("approvedReviews").asLong() > 0);

        // When: the public recalculation route is called
        HttpResponse<String> recalculateAsUser = request(
                "POST", "/workspaces/10/clean-score/recalculate", null, userToken);
        HttpResponse<String> recalculateAsAdmin = request(
                "POST", "/workspaces/10/clean-score/recalculate", null, adminToken);

        // Then: only an administrator can recalculate and receives the new summary
        assertEquals(403, recalculateAsUser.statusCode());
        assertEquals(200, recalculateAsAdmin.statusCode());
        assertEquals(88, objectMapper.readTree(recalculateAsAdmin.body()).path("cleanScore").asInt());
    }

    @Test
    void authAliasesValidateInputAndBlacklistLoggedOutToken() throws Exception {
        // Given: a valid user token
        String token = jwtUtil.generateToken("logout@example.com", 2L);

        // When: the new callback route receives no authorization code
        HttpResponse<String> invalidCallback = request("POST", "/auth/kakao/callback", "{}", null);

        // Then: it rejects the request without calling Kakao
        assertEquals(400, invalidCallback.statusCode());

        // When: the user logs out through the planned auth route
        HttpResponse<String> logout = request("POST", "/auth/logout", null, token);
        HttpResponse<String> afterLogout = request("GET", "/users/me/reviews", null, token);

        // Then: the token is blacklisted for protected APIs
        assertEquals(200, logout.statusCode());
        assertEquals(401, afterLogout.statusCode());
    }

    @Test
    void refreshingTokenRevokesThePreviousToken() throws Exception {
        // Given: a valid service token
        String oldToken = jwtUtil.generateToken("refresh@example.com", 2L);

        // When: the token is refreshed
        HttpResponse<String> refreshed = request("POST", "/auth/refresh", null, oldToken);
        String newToken = objectMapper.readTree(refreshed.body()).path("token").asString();

        // Then: only the rotated token remains usable
        assertEquals(200, refreshed.statusCode());
        assertEquals(401, request("GET", "/users/me/reviews", null, oldToken).statusCode());
        assertEquals(200, request("GET", "/users/me/reviews", null, newToken).statusCode());
    }

    private HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> multipart(
            String path,
            String fileName,
            String contentType,
            String content,
            String token
    ) throws Exception {
        String boundary = "CleanAlbaBoundary";
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n"
                + content + "\r\n--" + boundary + "--\r\n";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
