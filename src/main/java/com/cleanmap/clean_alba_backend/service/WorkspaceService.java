package com.cleanmap.clean_alba_backend.service;

import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.ReviewStatus;
import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;
import com.cleanmap.clean_alba_backend.dto.KakaoPlace;
import com.cleanmap.clean_alba_backend.dto.WorkspaceCreateRequest;
import com.cleanmap.clean_alba_backend.dto.WorkspaceListResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspacePlaceSearchResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspaceResolveRequest;
import com.cleanmap.clean_alba_backend.dto.WorkspaceResolveResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspaceSummaryResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspaceDetailResponse;
import com.cleanmap.clean_alba_backend.dto.ChecklistStatResponse;
import com.cleanmap.clean_alba_backend.dto.PublicReviewResponse;
import com.cleanmap.clean_alba_backend.repository.ReviewRepository;
import com.cleanmap.clean_alba_backend.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final ReviewRepository reviewRepository;
    private final KakaoPlaceService kakaoPlaceService;

    @Transactional(readOnly = true)
    public List<WorkspaceListResponse> getWorkspaceList(WorkspaceStatus status, String keyword) {
        String normalizedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        Integer minScore = (status != null) ? status.getMinScore() : null;
        Integer maxScore = (status != null) ? status.getMaxScoreExclusive() : null;

        List<Workspace> workspaces = workspaceRepository.search(minScore, maxScore, normalizedKeyword);

        return workspaces.stream()
                .map(WorkspaceListResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceSummaryResponse getWorkspaceSummary(Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Workspace not found."));
        List<Review> reviews = approvedReviews(workspaceId);
        WorkspaceSummaryResponse base = WorkspaceSummaryResponse.from(workspace);
        return new WorkspaceSummaryResponse(
                base.workspaceId(), base.name(), base.address(), base.category(), base.district(),
                base.latitude(), base.longitude(), base.cleanScore(), base.status(),
                reviews.size(), checklistStats(reviews)
        );
    }

    @Transactional(readOnly = true)
    public WorkspaceDetailResponse getWorkspaceDetail(Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Workspace not found."));
        List<Review> reviews = approvedReviews(workspaceId);
        WorkspaceSummaryResponse base = WorkspaceSummaryResponse.from(workspace);
        return new WorkspaceDetailResponse(
                base.workspaceId(), base.name(), base.address(), base.category(), base.district(),
                base.latitude(), base.longitude(), base.cleanScore(), base.status(),
                reviews.size(), checklistStats(reviews),
                reviews.stream().map(PublicReviewResponse::from).toList()
        );
    }

    // 신규 사업장 등록. 등록 직후엔 리뷰가 없어 cleanScore는 null(지도·목록 미노출).
    @Transactional
    public WorkspaceSummaryResponse createWorkspace(WorkspaceCreateRequest request) {
        if (isBlank(request.name()) || isBlank(request.address())
                || isBlank(request.category()) || isBlank(request.district())
                || request.latitude() == null || request.longitude() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "필수 항목이 누락되었습니다.");
        }

        Workspace workspace = new Workspace(
                request.name().trim(),
                request.address().trim(),
                request.category().trim(),
                request.district().trim(),
                request.latitude(),
                request.longitude());

        return WorkspaceSummaryResponse.from(workspaceRepository.save(workspace));
    }

    // 통합 장소 검색: 카카오 로컬 검색 결과를 kakaoPlaceId로 우리 DB와 대조해
    // 기존 사업장(existing=true)과 신규 카카오 장소(existing=false)를 함께 반환한다.
    @Transactional(readOnly = true)
    public List<WorkspacePlaceSearchResponse> searchPlaces(String keyword) {
        if (isBlank(keyword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어가 필요합니다.");
        }

        return kakaoPlaceService.search(keyword.trim()).stream()
                .map(place -> workspaceRepository.findByKakaoPlaceId(place.kakaoPlaceId())
                        .map(WorkspacePlaceSearchResponse::ofExisting)
                        .orElseGet(() -> WorkspacePlaceSearchResponse.ofNew(place)))
                .toList();
    }

    // 카카오 장소를 workspaceId로 변환한다. kakaoPlaceId가 이미 있으면 재사용, 없으면 생성.
    // 후기 작성 페이지(/review/write/:workspaceId) 진입 전에 호출된다.
    @Transactional
    public WorkspaceResolveResponse resolveByKakao(WorkspaceResolveRequest request) {
        if (isBlank(request.kakaoPlaceId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kakaoPlaceId가 필요합니다.");
        }
        String kakaoPlaceId = request.kakaoPlaceId().trim();

        Optional<Workspace> existing = workspaceRepository.findByKakaoPlaceId(kakaoPlaceId);
        if (existing.isPresent()) {
            return new WorkspaceResolveResponse(existing.get().getWorkspaceId(), false);
        }

        if (isBlank(request.name()) || isBlank(request.address()) || isBlank(request.category())
                || request.latitude() == null || request.longitude() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "장소 정보가 부족합니다.");
        }

        // district(상권)는 카카오가 제공하지 않으므로 null로 생성한다.
        Workspace workspace = new Workspace(
                request.name().trim(),
                request.address().trim(),
                request.category().trim(),
                null,
                request.latitude(),
                request.longitude(),
                kakaoPlaceId);
        try {
            Workspace saved = workspaceRepository.save(workspace);
            return new WorkspaceResolveResponse(saved.getWorkspaceId(), true);
        } catch (DataIntegrityViolationException raceException) {
            // 동시 요청으로 같은 kakaoPlaceId가 먼저 생성된 경우 → 재조회해 재사용
            Workspace created = workspaceRepository.findByKakaoPlaceId(kakaoPlaceId)
                    .orElseThrow(() -> raceException);
            return new WorkspaceResolveResponse(created.getWorkspaceId(), false);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // 특정 사업장의 클린지수를 승인된 리뷰들로부터 재계산해 저장한다.
    // 승인된 리뷰가 없으면 null(지도·목록 미노출). 리뷰 승인 시 호출된다.
    @Transactional
    public WorkspaceSummaryResponse recalculateCleanScore(Long workspaceId) {
        Workspace workspace = workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Workspace not found."));

        List<Review> approved = approvedReviews(workspaceId);

        Double cleanScore = approved.isEmpty()
                ? null
                : approved.stream().mapToDouble(Review::score).average().getAsDouble();

        workspace.updateCleanScore(cleanScore);
        WorkspaceSummaryResponse base = WorkspaceSummaryResponse.from(workspace);
        return new WorkspaceSummaryResponse(
                base.workspaceId(), base.name(), base.address(), base.category(), base.district(),
                base.latitude(), base.longitude(), base.cleanScore(), base.status(),
                approved.size(), checklistStats(approved)
        );
    }

    // 전체 사업장의 클린지수를 일괄 재계산한다. (시드 데이터 반영·정합성 보정용)
    @Transactional
    public void recalculateAllCleanScores() {
        workspaceRepository.findAll()
                .forEach(w -> recalculateCleanScore(w.getWorkspaceId()));
    }

    private List<Review> approvedReviews(Long workspaceId) {
        return reviewRepository.findByWorkspace_WorkspaceIdAndStatus(workspaceId, ReviewStatus.APPROVED);
    }

    private List<ChecklistStatResponse> checklistStats(List<Review> reviews) {
        return List.of(
                stat("CONTRACT", reviews, Review::isContractViolation),
                stat("MINIMUM_WAGE", reviews, Review::isMinimumWageViolation),
                stat("WEEKLY_ALLOWANCE", reviews, Review::isWeeklyAllowanceViolation),
                stat("BREAK_TIME", reviews, Review::isBreakTimeViolation),
                stat("WAGE_DELAY", reviews, Review::isWageDelayViolation),
                stat("SCHEDULE_CHANGE", reviews, Review::isScheduleChangeViolation),
                stat("SUBSTITUTE_COERCION", reviews, Review::isSubstituteCoercionViolation),
                stat("OVERTIME_PAY", reviews, Review::isOvertimePayViolation)
        );
    }

    private ChecklistStatResponse stat(String item, List<Review> reviews, Predicate<Review> violation) {
        long violationCount = reviews.stream().filter(violation).count();
        return new ChecklistStatResponse(item, reviews.size() - violationCount, violationCount);
    }
}
