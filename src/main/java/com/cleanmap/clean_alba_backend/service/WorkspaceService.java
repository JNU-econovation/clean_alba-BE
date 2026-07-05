package com.cleanmap.clean_alba_backend.service;

import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.ReviewStatus;
import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;
import com.cleanmap.clean_alba_backend.dto.WorkspaceCreateRequest;
import com.cleanmap.clean_alba_backend.dto.WorkspaceListResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspaceSummaryResponse;
import com.cleanmap.clean_alba_backend.repository.ReviewRepository;
import com.cleanmap.clean_alba_backend.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final ReviewRepository reviewRepository;

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

        return WorkspaceSummaryResponse.from(workspace);
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // 특정 사업장의 클린지수를 승인된 리뷰들로부터 재계산해 저장한다.
    // 승인된 리뷰가 없으면 null(지도·목록 미노출). 리뷰 승인 시 호출된다.
    @Transactional
    public void recalculateCleanScore(Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Workspace not found."));

        List<Review> approved = reviewRepository
                .findByWorkspace_WorkspaceIdAndStatus(workspaceId, ReviewStatus.APPROVED);

        Double cleanScore = approved.isEmpty()
                ? null
                : approved.stream().mapToDouble(Review::score).average().getAsDouble();

        workspace.updateCleanScore(cleanScore);
    }

    // 전체 사업장의 클린지수를 일괄 재계산한다. (시드 데이터 반영·정합성 보정용)
    @Transactional
    public void recalculateAllCleanScores() {
        workspaceRepository.findAll()
                .forEach(w -> recalculateCleanScore(w.getWorkspaceId()));
    }
}
