package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;

import java.math.BigDecimal;

// 통합 장소 검색 결과 한 건. 기존 사업장이면 existing=true(+workspaceId·cleanScore·status), 신규 카카오 장소면 false.
public record WorkspacePlaceSearchResponse(
    boolean existing,
    Long workspaceId,
    String kakaoPlaceId,
    String name,
    String address,
    String category,
    BigDecimal latitude,
    BigDecimal longitude,
    Integer cleanScore,
    WorkspaceStatus status
) {
    // 우리 DB에 없는 카카오 신규 장소
    public static WorkspacePlaceSearchResponse ofNew(KakaoPlace place) {
        return new WorkspacePlaceSearchResponse(
                false, null, place.kakaoPlaceId(),
                place.name(), place.address(), place.category(),
                place.latitude(), place.longitude(),
                null, null
        );
    }

    // 이미 등록된 사업장(우리 DB 데이터 기준)
    public static WorkspacePlaceSearchResponse ofExisting(Workspace workspace) {
        Double raw = workspace.getCleanScore();
        Integer score = (raw == null) ? null : (int) Math.round(raw);
        WorkspaceStatus status = (score == null) ? null : WorkspaceStatus.fromScore(score);
        return new WorkspacePlaceSearchResponse(
                true, workspace.getWorkspaceId(), workspace.getKakaoPlaceId(),
                workspace.getName(), workspace.getAddress(), workspace.getCategory(),
                workspace.getLatitude(), workspace.getLongitude(),
                score, status
        );
    }
}
