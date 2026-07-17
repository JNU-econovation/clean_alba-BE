package com.cleanmap.clean_alba_backend;

import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.dto.KakaoPlace;
import com.cleanmap.clean_alba_backend.dto.WorkspaceResolveRequest;
import com.cleanmap.clean_alba_backend.dto.WorkspaceResolveResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspaceSearchFilter;
import com.cleanmap.clean_alba_backend.repository.WorkspaceRepository;
import com.cleanmap.clean_alba_backend.service.KakaoPlaceService;
import com.cleanmap.clean_alba_backend.service.NaturalLanguageQueryParser;
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

    @MockitoBean
    private NaturalLanguageQueryParser naturalLanguageQueryParser;

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
    void placeSearchRejectsBlankKeyword() throws Exception {
        // Given/When: 공개 통합 검색을 공백 keyword로 호출하면 (토큰 없음)
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/workspaces/place-search?keyword=%20"))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Then: 카카오 호출 없이 400으로 거부한다
        assertEquals(400, response.statusCode());
    }

    // Solar의 해석 능력이 아니라, 해석 결과(WorkspaceSearchFilter)가 DB 검색 조건으로
    // 제대로 옮겨지는지를 검증한다. 그래서 파서는 목으로 대체하고 필터를 직접 주입한다.
    @Test
    void naturalLanguageSearchAppliesTheInterpretedFilter() throws Exception {
        // Given: 파서가 "60점 이상 + 상대 + 카페"로 해석했다고 가정하고,
        //        점수·상권·업종이 각각 하나씩 어긋나는 사업장들을 함께 저장한다
        when(naturalLanguageQueryParser.parse("클린점수 60점 넘는 상대 카페 찾아줘"))
                .thenReturn(new WorkspaceSearchFilter(60, null, "상대", "카페", null));

        Workspace match = save("자연어검색 상대카페 우수", "카페", "상대", 88.0);
        Workspace lowScore = save("자연어검색 상대카페 주의", "카페", "상대", 50.0);
        Workspace otherDistrict = save("자연어검색 예대카페 우수", "카페", "예대", 90.0);
        Workspace otherCategory = save("자연어검색 상대식당 우수", "식당", "상대", 85.0);

        try {
            // When: 자연어 검색을 호출하면
            JsonNode response = getJson("/workspaces/nl-search?query="
                    + URLEncoder.encode("클린점수 60점 넘는 상대 카페 찾아줘", StandardCharsets.UTF_8));

            // Then: 해석한 조건이 그대로 응답되고, 세 조건을 모두 만족하는 사업장만 돌아온다
            assertEquals(60, response.path("interpreted").path("minScore").asInt());
            assertEquals("상대", response.path("interpreted").path("district").asText());
            assertEquals("카페", response.path("interpreted").path("category").asText());

            JsonNode results = response.path("results");
            assertTrue(containsWorkspace(results, match.getWorkspaceId()));
            assertFalse(containsWorkspace(results, lowScore.getWorkspaceId()));
            assertFalse(containsWorkspace(results, otherDistrict.getWorkspaceId()));
            assertFalse(containsWorkspace(results, otherCategory.getWorkspaceId()));
        } finally {
            workspaceRepository.deleteAll(List.of(match, lowScore, otherDistrict, otherCategory));
        }
    }

    // minScore는 응답에 노출되는 반올림 점수 기준이라 저장 점수에서 0.5를 빼서 비교한다.
    // 저장 59.5는 60으로 반올림되어 노출되므로 "60점 이상"에 포함되어야 하고,
    // 59.4는 59로 노출되므로 빠져야 한다.
    @Test
    void naturalLanguageSearchIncludesScoresThatRoundUpToTheMinimum() throws Exception {
        // Given: 파서가 "60점 이상 카페"로 해석했고, 반올림 경계에 걸친 점수들이 저장돼 있다
        when(naturalLanguageQueryParser.parse("60점 이상 카페"))
                .thenReturn(new WorkspaceSearchFilter(60, null, null, "카페", null));

        Workspace roundsUp = save("자연어검색 경계 포함", "카페", "상대", 59.5);
        Workspace roundsDown = save("자연어검색 경계 제외", "카페", "상대", 59.4);

        try {
            // When: 자연어 검색을 호출하면
            JsonNode response = getJson("/workspaces/nl-search?query="
                    + URLEncoder.encode("60점 이상 카페", StandardCharsets.UTF_8));

            // Then: 60으로 노출되는 사업장만 포함된다(노출 점수와 필터 기준이 일치)
            JsonNode results = response.path("results");
            assertTrue(containsWorkspace(results, roundsUp.getWorkspaceId()));
            assertFalse(containsWorkspace(results, roundsDown.getWorkspaceId()));
            assertEquals(60, scoreOf(results, roundsUp.getWorkspaceId()));
        } finally {
            workspaceRepository.deleteAll(List.of(roundsUp, roundsDown));
        }
    }

    private Workspace save(String name, String category, String district, Double cleanScore) {
        Workspace workspace = new Workspace(
                name, "광주광역시 북구 테스트로 1", category, district,
                new BigDecimal("35.5000000"), new BigDecimal("126.5000000"));
        workspace.updateCleanScore(cleanScore);
        return workspaceRepository.saveAndFlush(workspace);
    }

    private int scoreOf(JsonNode results, Long workspaceId) {
        for (JsonNode result : results) {
            if (result.path("workspaceId").asLong() == workspaceId) {
                return result.path("cleanScore").asInt();
            }
        }
        throw new AssertionError("workspace " + workspaceId + " not found in results");
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
