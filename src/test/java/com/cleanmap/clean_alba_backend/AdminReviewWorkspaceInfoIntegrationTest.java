package com.cleanmap.clean_alba_backend;

import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.dto.ReviewCreateRequest;
import com.cleanmap.clean_alba_backend.repository.ReviewRepository;
import com.cleanmap.clean_alba_backend.repository.WorkspaceRepository;
import com.cleanmap.clean_alba_backend.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
class AdminReviewWorkspaceInfoIntegrationTest {

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final List<Long> workspaceIds = new ArrayList<>();
    private final List<Long> reviewIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        reviewRepository.deleteAllById(reviewIds);
        workspaceRepository.deleteAllById(workspaceIds);
    }

    @Test
    void adminReviewListAndDetailIncludeWorkspaceInformation() throws Exception {
        Workspace workspace = saveWorkspace("Admin Cafe", "Cafe", "Gwangju Buk-gu", "Gwangju Buk-gu Test-ro 1");
        Review review = saveReview(workspace);

        JsonNode listedReview = findListedReview(review.getReviewId());
        JsonNode detailedReview = getDetail(review.getReviewId());

        assertWorkspaceInformation(listedReview, workspace, "Cafe", "Gwangju Buk-gu", "Gwangju Buk-gu Test-ro 1");
        assertWorkspaceInformation(detailedReview, workspace, "Cafe", "Gwangju Buk-gu", "Gwangju Buk-gu Test-ro 1");
        assertEquals(review.getReviewId(), detailedReview.path("reviewId").asLong());
        assertEquals("admin-review-workspace@example.com", detailedReview.path("authorEmail").asString());
        assertEquals("PENDING", detailedReview.path("status").asString());
        assertTrue(detailedReview.path("contractViolation").isBoolean());
        assertEquals(0, detailedReview.path("attachmentCount").asInt());
    }

    @Test
    void adminReviewListAndDetailAllowNullWorkspaceInformation() throws Exception {
        Workspace workspace = saveWorkspace("Missing Workspace Information Cafe", "Cafe", "Gwangju Buk-gu", "Gwangju Buk-gu Test-ro 2");
        Review review = saveReview(workspace);
        allowNullWorkspaceInformation();
        jdbcTemplate.update(
                "UPDATE workspaces SET category = NULL, district = NULL, address = NULL WHERE workspace_id = ?",
                workspace.getWorkspaceId()
        );

        JsonNode listedReview = findListedReview(review.getReviewId());
        JsonNode detailedReview = getDetail(review.getReviewId());

        assertTrue(listedReview.path("category").isNull());
        assertTrue(listedReview.path("district").isNull());
        assertTrue(listedReview.path("address").isNull());
        assertTrue(detailedReview.path("category").isNull());
        assertTrue(detailedReview.path("district").isNull());
        assertTrue(detailedReview.path("address").isNull());
    }

    private Workspace saveWorkspace(String name, String category, String district, String address) {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace(
                name + " " + UUID.randomUUID(), address, category, district,
                new BigDecimal("35.1000000"), new BigDecimal("126.1000000")
        ));
        workspaceIds.add(workspace.getWorkspaceId());
        return workspace;
    }

    private Review saveReview(Workspace workspace) {
        Review review = reviewRepository.saveAndFlush(new Review(
                workspace,
                new ReviewCreateRequest(false, false, false, false, false, false, false, false, 1, "Review content"),
                "admin-review-workspace@example.com"
        ));
        reviewIds.add(review.getReviewId());
        return review;
    }

    private void allowNullWorkspaceInformation() {
        jdbcTemplate.execute("ALTER TABLE workspaces ALTER COLUMN category DROP NOT NULL");
        jdbcTemplate.execute("ALTER TABLE workspaces ALTER COLUMN address DROP NOT NULL");
    }

    private JsonNode findListedReview(Long reviewId) throws Exception {
        HttpResponse<String> response = get("/admin/reviews?status=PENDING");
        assertEquals(200, response.statusCode());
        for (JsonNode review : objectMapper.readTree(response.body()).path("content")) {
            if (review.path("reviewId").asLong() == reviewId) {
                return review;
            }
        }
        throw new AssertionError("Review not found in the admin review list");
    }

    private JsonNode getDetail(Long reviewId) throws Exception {
        HttpResponse<String> response = get("/admin/reviews/" + reviewId);
        assertEquals(200, response.statusCode());
        return objectMapper.readTree(response.body());
    }

    private void assertWorkspaceInformation(
            JsonNode response, Workspace workspace, String category, String district, String address
    ) {
        assertEquals(workspace.getWorkspaceId(), response.path("workspaceId").asLong());
        assertEquals(workspace.getName(), response.path("workspaceName").asString());
        assertEquals(category, response.path("category").asString());
        assertEquals(district, response.path("district").asString());
        assertEquals(address, response.path("address").asString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + jwtUtil.generateToken("admin@example.com", 1L))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
