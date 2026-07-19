package com.cleanmap.clean_alba_backend;

import com.cleanmap.clean_alba_backend.domain.DayType;
import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.ReviewStatus;
import com.cleanmap.clean_alba_backend.domain.TimeSlot;
import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.dto.ReviewCreateRequest;
import com.cleanmap.clean_alba_backend.repository.ReviewRepository;
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
import java.math.BigDecimal;

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
class SimultaneousWorkerStatsIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void workspaceDetailReturnsStatsFromOnlyApprovedUsableReviews() throws Exception {
        // Given: reviews across statuses and data completeness for one workspace
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace(
                "simultaneous-worker-stats", "광주광역시 북구 용봉로 1", "카페", "용봉동",
                new BigDecimal("35.1800000"), new BigDecimal("126.9100000")));
        saveReview(workspace, ReviewStatus.APPROVED, new ReviewFixture(DayType.WEEKDAY, TimeSlot.MORNING, 2));
        saveReview(workspace, ReviewStatus.APPROVED, new ReviewFixture(DayType.WEEKDAY, TimeSlot.MORNING, 3));
        saveReview(workspace, ReviewStatus.APPROVED, new ReviewFixture(DayType.WEEKDAY, TimeSlot.AFTERNOON, 4));
        saveReview(workspace, ReviewStatus.APPROVED, new ReviewFixture(DayType.WEEKEND, TimeSlot.NIGHT, null));
        saveReview(workspace, ReviewStatus.PENDING, new ReviewFixture(DayType.WEEKDAY, TimeSlot.MORNING, 100));
        saveReview(workspace, ReviewStatus.REJECTED, new ReviewFixture(DayType.WEEKDAY, TimeSlot.MORNING, 200));

        // When: the public workspace detail API is requested
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/workspaces/" + workspace.getWorkspaceId()))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        JsonNode stats = objectMapper.readTree(response.body()).path("simultaneousWorkerStats");

        // Then: only approved reviews with all aggregation fields contribute to each average
        assertEquals(200, response.statusCode());
        assertEquals(2, stats.size());
        assertStat(stats, "weekday", "morning", 2.5, 2);
        assertStat(stats, "weekday", "afternoon", 4.0, 1);
    }

    @Test
    void workspaceDetailReturnsAnEmptyStatsListWhenNoUsableReviewExists() throws Exception {
        // Given: a workspace with no reviews
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace(
                "empty-simultaneous-worker-stats", "광주광역시 북구 용봉로 2", "카페", "용봉동",
                new BigDecimal("35.1800001"), new BigDecimal("126.9100001")));

        // When: the public workspace detail API is requested
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/workspaces/" + workspace.getWorkspaceId()))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // Then: its stats field is present and empty
        assertEquals(200, response.statusCode());
        assertTrue(objectMapper.readTree(response.body()).path("simultaneousWorkerStats").isEmpty());
    }

    private void saveReview(Workspace workspace, ReviewStatus status, ReviewFixture fixture) {
        Review review = new Review(workspace, new ReviewCreateRequest(
                false, false, false, false, false, false, false, false,
                fixture.coworkerCount(), "동시간대 근무자 수", fixture.dayType(), fixture.timeSlot()), "kakao:stats");
        if (status != ReviewStatus.PENDING) {
            review.moderate(status);
        }
        reviewRepository.saveAndFlush(review);
    }

    private void assertStat(
            JsonNode stats,
            String dayType,
            String timeSlot,
            double averageCoworkerCount,
            int reviewCount
    ) {
        for (JsonNode stat : stats) {
            if (dayType.equals(stat.path("dayType").asString())
                    && timeSlot.equals(stat.path("timeSlot").asString())) {
                assertEquals(averageCoworkerCount, stat.path("averageCoworkerCount").asDouble());
                assertEquals(reviewCount, stat.path("reviewCount").asInt());
                return;
            }
        }
        throw new AssertionError("Expected simultaneous worker stat was not returned.");
    }

    private record ReviewFixture(DayType dayType, TimeSlot timeSlot, Integer coworkerCount) {
    }
}
