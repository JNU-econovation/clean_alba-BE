package com.cleanmap.clean_alba_backend.service;

import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.ReviewStatus;
import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;
import com.cleanmap.clean_alba_backend.dto.KakaoPlace;
import com.cleanmap.clean_alba_backend.dto.WorkspaceCreateRequest;
import com.cleanmap.clean_alba_backend.dto.WorkspaceListResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspaceNlSearchResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspacePlaceSearchResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspaceSearchFilter;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

    private final WorkspaceRepository workspaceRepository;
    private final ReviewRepository reviewRepository;
    private final KakaoPlaceService kakaoPlaceService;
    private final WorkspaceResolveWriter workspaceResolveWriter;
    private final NaturalLanguageQueryParser naturalLanguageQueryParser;

    @Transactional(readOnly = true)
    public List<WorkspaceListResponse> getWorkspaceList(WorkspaceStatus status, String keyword) {
        String normalizedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        Double minScore = (status != null) ? status.getMinStoredScore() : null;
        Double maxScore = (status != null) ? status.getMaxStoredScoreExclusive() : null;

        List<Workspace> workspaces = workspaceRepository.search(minScore, maxScore, normalizedKeyword);

        return workspaces.stream()
                .map(WorkspaceListResponse::from)
                .toList();
    }

    // 자연어 검색: Solar가 검색어를 조건으로 해석하고, 검색 자체는 DB가 수행한다.
    // 점수 조건은 응답에 노출되는 반올림 점수 기준이므로 저장 점수 기준으로 ±0.5 보정한다.
    @Transactional(readOnly = true)
    public WorkspaceNlSearchResponse naturalLanguageSearch(String query) {
        WorkspaceSearchFilter filter = naturalLanguageQueryParser.parse(query);

        Double minStoredScore = (filter.minScore() == null) ? null : filter.minScore() - 0.5;
        Double maxStoredScoreExclusive = (filter.maxScore() == null) ? null : filter.maxScore() + 0.5;

        List<Workspace> workspaces = workspaceRepository.searchByFilter(
                minStoredScore, maxStoredScoreExclusive,
                filter.district(), filter.category(), filter.keyword());

        return new WorkspaceNlSearchResponse(
                filter,
                workspaces.stream().map(WorkspaceListResponse::from).toList());
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
                reviews.size(), checklistStats(reviews), reviewSummary(reviews)
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

        String normalizedKeyword = keyword.trim();
        List<Workspace> existing = workspaceRepository.searchAllByKeyword(normalizedKeyword);
        List<WorkspacePlaceSearchResponse> results = new ArrayList<>(
                existing.stream().map(WorkspacePlaceSearchResponse::ofExisting).toList());
        Set<Long> addedWorkspaceIds = new HashSet<>(
                existing.stream().map(Workspace::getWorkspaceId).toList());

        List<KakaoPlace> kakaoPlaces;
        try {
            kakaoPlaces = kakaoPlaceService.search(normalizedKeyword);
        } catch (ResponseStatusException exception) {
            if (results.isEmpty()) {
                throw exception;
            }
            log.warn("카카오 장소 검색 실패로 DB 검색 결과만 반환합니다. status={}",
                    exception.getStatusCode().value());
            return results;
        }

        for (KakaoPlace place : kakaoPlaces) {
            Optional<Workspace> matched = workspaceRepository.findByKakaoPlaceId(place.kakaoPlaceId())
                    .or(() -> existing.stream()
                            .filter(workspace -> samePlace(workspace, place))
                            .findFirst());
            if (matched.isPresent()) {
                Workspace workspace = matched.get();
                if (addedWorkspaceIds.add(workspace.getWorkspaceId())) {
                    results.add(WorkspacePlaceSearchResponse.ofExisting(workspace));
                }
            } else {
                results.add(WorkspacePlaceSearchResponse.ofNew(place));
            }
        }
        return results;
    }

    // 카카오 장소를 workspaceId로 변환한다. kakaoPlaceId가 이미 있으면 재사용, 없으면 생성.
    // 후기 작성 페이지(/review/write/:workspaceId) 진입 전에 호출된다.
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

        try {
            return workspaceResolveWriter.create(request, kakaoPlaceId);
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

    private static boolean samePlace(Workspace workspace, KakaoPlace place) {
        return workspace.getName().equals(place.name())
                && workspace.getAddress().equals(place.address());
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
                approved.size(), checklistStats(approved), reviewSummary(approved)
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

    private String reviewSummary(List<Review> reviews) {
        return reviews.stream()
                .map(Review::getContent)
                .filter(content -> content != null && !content.isBlank())
                .findFirst()
                .orElse(null);
    }
}
