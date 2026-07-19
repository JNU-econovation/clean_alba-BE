package com.cleanmap.clean_alba_backend;

import com.cleanmap.clean_alba_backend.util.JwtUtil;
import com.cleanmap.clean_alba_backend.repository.WorkspaceRepository;
import com.cleanmap.clean_alba_backend.repository.ReviewAttachmentRepository;
import com.cleanmap.clean_alba_backend.repository.ReviewRepository;
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
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Autowired
    private ReviewAttachmentRepository reviewAttachmentRepository;

    @Autowired
    private ReviewRepository reviewRepository;

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
        long attachmentId = objectMapper.readTree(attachment.body()).path("attachmentId").asLong();
        assertEquals(reviewId, objectMapper.readTree(attachment.body()).path("reviewId").asLong());
        var storedAttachment = reviewAttachmentRepository.findById(attachmentId).orElseThrow();
        assertTrue(storedAttachment.getStorageKey().startsWith("reviews/" + reviewId + "/"));
        assertEquals(null, storedAttachment.getContent());
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
        JsonNode queueContent = objectMapper.readTree(queue.body()).path("content");
        JsonNode queuedReview = null;
        for (int i = 0; i < queueContent.size(); i++) {
            if (queueContent.get(i).path("reviewId").asLong() == reviewId) {
                queuedReview = queueContent.get(i);
            }
        }
        assertNotNull(queuedReview);
        assertEquals(1, queuedReview.path("attachmentCount").asInt());
        assertEquals(200, detail.statusCode());
        JsonNode detailBody = objectMapper.readTree(detail.body());
        attachmentId = detailBody.path("attachments").get(0).path("attachmentId").asLong();
        assertEquals("proof.pdf", detailBody.path("attachments").get(0).path("fileName").asString());
        assertEquals("application/pdf", detailBody.path("attachments").get(0).path("contentType").asString());
        assertEquals(8, detailBody.path("attachments").get(0).path("size").asInt());

        HttpResponse<byte[]> downloaded = download(
                "/admin/reviews/" + reviewId + "/attachments/" + attachmentId, adminToken);
        assertEquals(200, downloaded.statusCode());
        assertEquals("%PDF-1.4", new String(downloaded.body(), StandardCharsets.UTF_8));
        assertEquals(403, download(
                "/admin/reviews/" + reviewId + "/attachments/" + attachmentId, userToken).statusCode());
        assertEquals(404, download(
                "/admin/reviews/" + (reviewId + 1) + "/attachments/" + attachmentId, adminToken).statusCode());
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
    void adminEditsSubjectiveReviewContentOnly() throws Exception {
        // Given: a dedicated workspace with a pending review that has content and a violation
        String userToken = jwtUtil.generateToken("content-editor-user@example.com", 2L);
        String adminToken = jwtUtil.generateToken("admin@example.com", 1L);
        HttpResponse<String> workspaceCreated = request("POST", "/workspaces", """
                {
                  "name": "후기수정 테스트 매장",
                  "address": "광주광역시 북구 수정로 1",
                  "category": "카페",
                  "district": "전대후문",
                  "latitude": 35.2000000,
                  "longitude": 126.2000000
                }
                """, adminToken);
        long workspaceId = objectMapper.readTree(workspaceCreated.body()).path("workspaceId").asLong();
        HttpResponse<String> created = request("POST", "/workspaces/" + workspaceId + "/reviews", """
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
                  "content": "원본 후기입니다.",
                  "dayType": "weekday",
                  "timeSlot": "morning"
                }
                """, userToken);
        assertEquals(201, created.statusCode());
        long reviewId = objectMapper.readTree(created.body()).path("reviewId").asLong();

        // When/Then: authentication, authorization, and input validation guard the route
        assertEquals(401, request("PATCH", "/admin/reviews/" + reviewId + "/content",
                "{\"content\":\"수정\"}", null).statusCode());
        assertEquals(403, request("PATCH", "/admin/reviews/" + reviewId + "/content",
                "{\"content\":\"수정\"}", userToken).statusCode());
        assertEquals(404, request("PATCH", "/admin/reviews/" + (reviewId + 9999) + "/content",
                "{\"content\":\"수정\"}", adminToken).statusCode());
        assertEquals(400, request("PATCH", "/admin/reviews/" + reviewId + "/content",
                "{}", adminToken).statusCode());
        assertEquals(400, request("PATCH", "/admin/reviews/" + reviewId + "/content",
                "{\"content\":null}", adminToken).statusCode());

        // When: an administrator edits the content with surrounding whitespace
        HttpResponse<String> updated = request("PATCH", "/admin/reviews/" + reviewId + "/content",
                "{\"content\":\"  관리자가 수정한 후기 내용  \"}", adminToken);

        // Then: the trimmed content and updatedAt are returned and persisted
        assertEquals(200, updated.statusCode());
        JsonNode updatedBody = objectMapper.readTree(updated.body());
        assertEquals(reviewId, updatedBody.path("reviewId").asLong());
        assertEquals("관리자가 수정한 후기 내용", updatedBody.path("content").asString());
        assertTrue(!updatedBody.path("updatedAt").asString().isBlank());
        var storedReview = reviewRepository.findById(reviewId).orElseThrow();
        assertEquals("관리자가 수정한 후기 내용", storedReview.getContent());
        assertTrue(!storedReview.getUpdatedAt().isBefore(storedReview.getCreatedAt()));

        // Then: only the subjective content changed - other fields stay intact
        JsonNode adminDetail = objectMapper.readTree(
                request("GET", "/admin/reviews/" + reviewId, null, adminToken).body());
        assertEquals(true, adminDetail.path("breakTimeViolation").asBoolean());
        assertEquals(2, adminDetail.path("coworkerCount").asInt());
        assertEquals("PENDING", adminDetail.path("status").asString());
        assertEquals("kakao:2", adminDetail.path("authorEmail").asString());

        // When: an administrator sends a blank content
        HttpResponse<String> blanked = request("PATCH", "/admin/reviews/" + reviewId + "/content",
                "{\"content\":\"   \"}", adminToken);

        // Then: the content is stored as null
        assertEquals(200, blanked.statusCode());
        assertTrue(objectMapper.readTree(blanked.body()).path("content").isNull());
        assertEquals(null, reviewRepository.findById(reviewId).orElseThrow().getContent());

        // When: the review is approved and its content is edited afterwards
        assertEquals(200, request("PATCH", "/admin/reviews/" + reviewId + "/status",
                "{\"status\":\"APPROVED\"}", adminToken).statusCode());
        HttpResponse<String> editedAfterApproval = request("PATCH", "/admin/reviews/" + reviewId + "/content",
                "{\"content\":\"승인 후 수정된 후기\"}", adminToken);

        // Then: the edit succeeds, the public detail reflects it, and the score stays put
        assertEquals(200, editedAfterApproval.statusCode());
        JsonNode publicDetail = objectMapper.readTree(
                request("GET", "/workspaces/" + workspaceId, null, null).body());
        assertEquals("승인 후 수정된 후기", publicDetail.path("reviews").get(0).path("content").asString());
        assertEquals(88, publicDetail.path("cleanScore").asInt());

        // When: a separate review is rejected, then its content is edited
        HttpResponse<String> rejectedReviewCreated = request("POST", "/workspaces/" + workspaceId + "/reviews", """
                {
                  "contractViolation": false,
                  "minimumWageViolation": false,
                  "weeklyAllowanceViolation": false,
                  "breakTimeViolation": false,
                  "wageDelayViolation": false,
                  "scheduleChangeViolation": false,
                  "substituteCoercionViolation": false,
                  "overtimePayViolation": false,
                  "coworkerCount": 1,
                  "content": "반려 전 후기",
                  "dayType": "weekday",
                  "timeSlot": "afternoon"
                }
                """, userToken);
        long rejectedReviewId = objectMapper.readTree(rejectedReviewCreated.body()).path("reviewId").asLong();
        assertEquals(200, request("PATCH", "/admin/reviews/" + rejectedReviewId + "/status",
                "{\"status\":\"REJECTED\"}", adminToken).statusCode());

        HttpResponse<String> editedAfterRejection = request("PATCH", "/admin/reviews/" + rejectedReviewId + "/content",
                "{\"content\":\"반려 후 수정된 후기\"}", adminToken);

        // Then: rejected reviews are editable without changing the approved review score
        assertEquals(200, editedAfterRejection.statusCode());
        assertEquals("반려 후 수정된 후기", objectMapper.readTree(editedAfterRejection.body()).path("content").asString());
        assertEquals(88, objectMapper.readTree(
                request("GET", "/workspaces/" + workspaceId, null, null).body()).path("cleanScore").asInt());
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

    private HttpResponse<byte[]> download(String path, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
}
