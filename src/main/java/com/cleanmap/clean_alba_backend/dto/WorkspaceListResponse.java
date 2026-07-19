package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;
import com.cleanmap.clean_alba_backend.domain.ReviewSentiment;
import java.math.BigDecimal;

/** 지도 목록에서 사업장 위치, 표시용 점수와 상태를 함께 전달하는 응답이다. */
public record WorkspaceListResponse(
    Long workspaceId,
    String name,
    String address,
    String category,
    String district,
    BigDecimal latitude,
    BigDecimal longitude,
    Integer cleanScore,
    WorkspaceStatus status,
    ReviewSentiment dominantReviewSentiment
) {
    /** 저장된 실수 평균을 화면 표시용 정수로 반올림한 뒤 상태 구간을 계산한다. */
    public static WorkspaceListResponse from(Workspace workspace, ReviewSentiment dominantReviewSentiment) {
        Double raw = workspace.getCleanScore();
        Integer displayScore = (raw == null) ? null : (int) Math.round(raw);
        WorkspaceStatus status = (displayScore == null) ? null : WorkspaceStatus.fromScore(displayScore);
        return new WorkspaceListResponse(
            workspace.getWorkspaceId(),
            workspace.getName(),
            workspace.getAddress(),
            workspace.getCategory(),
            workspace.getDistrict(),
            workspace.getLatitude(),
            workspace.getLongitude(),
            displayScore,
            status,
            dominantReviewSentiment
        );
    }
}
