package com.cleanmap.clean_alba_backend.controller;

import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;
import com.cleanmap.clean_alba_backend.dto.WorkspaceCreateRequest;
import com.cleanmap.clean_alba_backend.dto.WorkspaceListResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspaceNlSearchResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspacePlaceSearchResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspaceResolveRequest;
import com.cleanmap.clean_alba_backend.dto.WorkspaceResolveResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspaceSummaryResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspaceDetailResponse;
import com.cleanmap.clean_alba_backend.dto.ReviewCreateRequest;
import com.cleanmap.clean_alba_backend.dto.ReviewCreateResponse;
import com.cleanmap.clean_alba_backend.service.AuthService;
import com.cleanmap.clean_alba_backend.service.ReviewService;
import com.cleanmap.clean_alba_backend.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 사업장 등록, 목록 검색, 상세 요약 HTTP API를 제공한다. */
@RestController
@RequestMapping("/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final ReviewService reviewService;
    private final AuthService authService;

    // POST /workspaces — 신규 사업장 등록 (관리자 전용)
    @PostMapping
    public ResponseEntity<?> createWorkspace(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @RequestBody WorkspaceCreateRequest request
    ) {
        authService.requireAdmin(authorizationHeader);
        WorkspaceSummaryResponse created = workspaceService.createWorkspace(request);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping
    public List<WorkspaceListResponse> getWorkspaceList(
        @RequestParam(required = false) WorkspaceStatus status,
        @RequestParam(required = false) String keyword
    ) {
        return workspaceService.getWorkspaceList(status, keyword);
    }

    // GET /workspaces/nl-search — 자연어 검색 ("클린점수 60점 넘는 상대 카페")
    @GetMapping("/nl-search")
    public WorkspaceNlSearchResponse searchByNaturalLanguage(
        @RequestParam String query
    ) {
        return workspaceService.naturalLanguageSearch(query);
    }

    // GET /workspaces/place-search — 기존 사업장 + 카카오 신규 장소 통합 검색 (후기 장소 선택 페이지)
    @GetMapping("/place-search")
    public List<WorkspacePlaceSearchResponse> searchPlaces(
        @RequestParam String keyword
    ) {
        return workspaceService.searchPlaces(keyword);
    }

    // POST /workspaces/resolve — 카카오 장소를 workspaceId로 변환 (중복 시 재사용). 로그인 필요.
    @PostMapping("/resolve")
    public WorkspaceResolveResponse resolve(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @RequestBody WorkspaceResolveRequest request
    ) {
        authService.authenticate(authorizationHeader);
        return workspaceService.resolveByKakao(request);
    }

    @GetMapping("/{workspaceId}/summary")
    public WorkspaceSummaryResponse getWorkspaceSummary(
        @PathVariable Long workspaceId
    ) {
        return workspaceService.getWorkspaceSummary(workspaceId);
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceDetailResponse getWorkspaceDetail(@PathVariable Long workspaceId) {
        return workspaceService.getWorkspaceDetail(workspaceId);
    }

    @PostMapping("/{workspaceId}/reviews")
    public ResponseEntity<ReviewCreateResponse> createReview(
            @PathVariable Long workspaceId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody ReviewCreateRequest request
    ) {
        AuthService.AuthenticatedUser user = authService.authenticate(authorizationHeader);
        return ResponseEntity.status(201).body(reviewService.create(workspaceId, request, user.authorKey()));
    }

    @PostMapping("/{workspaceId}/clean-score/recalculate")
    public WorkspaceSummaryResponse recalculateCleanScore(
            @PathVariable Long workspaceId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        authService.requireAdmin(authorizationHeader);
        return workspaceService.recalculateCleanScore(workspaceId);
    }
}
