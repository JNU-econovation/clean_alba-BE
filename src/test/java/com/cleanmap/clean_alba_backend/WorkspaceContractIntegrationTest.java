package com.cleanmap.clean_alba_backend;

import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.dto.KakaoPlace;
import com.cleanmap.clean_alba_backend.dto.WorkspaceResolveRequest;
import com.cleanmap.clean_alba_backend.dto.WorkspaceResolveResponse;
import com.cleanmap.clean_alba_backend.repository.WorkspaceRepository;
import com.cleanmap.clean_alba_backend.service.KakaoPlaceService;
import com.cleanmap.clean_alba_backend.service.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

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
class WorkspaceContractIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KakaoPlaceService kakaoPlaceService;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void roundedScoreUsesTheSameStatusForFilteringAndResponse() throws Exception {
        // Given: a stored score that rounds across the EXCELLENT boundary
        Workspace workspace = new Workspace(
                "반올림 경계 사업장", "광주 북구 테스트로 1", "카페", "상대",
                new BigDecimal("35.1000000"), new BigDecimal("126.1000000"));
        workspace.updateCleanScore(79.5);
        Workspace saved = workspaceRepository.saveAndFlush(workspace);

        try {
            // When: clients filter by each adjacent status
            JsonNode excellent = getJson("/workspaces?status=EXCELLENT");
            JsonNode normal = getJson("/workspaces?status=NORMAL");

            // Then: the record appears only under the status returned in its JSON
            assertTrue(containsWorkspace(excellent, saved.getWorkspaceId()));
            assertFalse(containsWorkspace(normal, saved.getWorkspaceId()));
        } finally {
            workspaceRepository.deleteById(saved.getWorkspaceId());
        }
    }

    @Test
    void placeSearchCombinesExistingDatabaseAndNewKakaoPlaces() throws Exception {
        // Given: a DB-only workspace and a distinct Kakao search result
        String keyword = "통합검색카페";
        Workspace existing = workspaceRepository.saveAndFlush(new Workspace(
                keyword, "광주 북구 기존로 1", "카페", "예대",
                new BigDecimal("35.2000000"), new BigDecimal("126.2000000")));
        when(kakaoPlaceService.search(keyword)).thenReturn(List.of(new KakaoPlace(
                "kakao-new-place", "카카오 신규 카페", "광주 북구 신규로 2", "카페",
                new BigDecimal("35.3000000"), new BigDecimal("126.3000000"))));

        try {
            // When: the integrated place search is called
            JsonNode results = getJson("/workspaces/place-search?keyword="
                    + URLEncoder.encode(keyword, StandardCharsets.UTF_8));

            // Then: both the existing workspace and the new Kakao place are returned
            assertEquals(2, results.size());
            assertTrue(containsWorkspace(results, existing.getWorkspaceId()));
            assertTrue(containsKakaoPlace(results, "kakao-new-place"));
        } finally {
            workspaceRepository.deleteById(existing.getWorkspaceId());
        }
    }

    @Test
    void concurrentResolveCreatesOnceAndReusesTheWinner() throws Exception {
        // Given: two callers resolve the same new Kakao place at the same time
        String kakaoPlaceId = "concurrent-" + UUID.randomUUID();
        WorkspaceResolveRequest request = new WorkspaceResolveRequest(
                kakaoPlaceId, "동시 등록 카페", "광주 북구 동시로 1", "카페",
                new BigDecimal("35.4000000"), new BigDecimal("126.4000000"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<WorkspaceResolveResponse> first = executor.submit(() -> resolveTogether(request, ready, start));
            Future<WorkspaceResolveResponse> second = executor.submit(() -> resolveTogether(request, ready, start));
            ready.await();

            // When: both inserts race
            start.countDown();
            List<WorkspaceResolveResponse> results = List.of(first.get(), second.get());

            // Then: both succeed with one shared ID and exactly one creation
            assertEquals(results.get(0).workspaceId(), results.get(1).workspaceId());
            assertEquals(1, results.stream().filter(WorkspaceResolveResponse::created).count());
        } finally {
            executor.shutdownNow();
            workspaceRepository.findByKakaoPlaceId(kakaoPlaceId).ifPresent(workspaceRepository::delete);
        }
    }

    private WorkspaceResolveResponse resolveTogether(
            WorkspaceResolveRequest request,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return workspaceService.resolveByKakao(request);
    }

    private JsonNode getJson(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return objectMapper.readTree(response.body());
    }

    private boolean containsWorkspace(JsonNode results, Long workspaceId) {
        for (JsonNode result : results) {
            if (result.path("workspaceId").asLong() == workspaceId) {
                return true;
            }
        }
        return false;
    }

    private boolean containsKakaoPlace(JsonNode results, String kakaoPlaceId) {
        for (JsonNode result : results) {
            if (kakaoPlaceId.equals(result.path("kakaoPlaceId").asText())) {
                return true;
            }
        }
        return false;
    }
}
