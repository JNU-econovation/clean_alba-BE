package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;
import java.math.BigDecimal;

/** 사업장 상세 화면의 요약 영역에 필요한 기본 정보와 클린지수를 전달한다. */
public record WorkspaceSummaryResponse(
    Long workspaceId,
    String name,
    String address,
    String category,
    String district,
    BigDecimal latitude,
    BigDecimal longitude,
    Integer cleanScore,
    WorkspaceStatus status
) {
    /** 엔티티를 외부 응답으로 변환하면서 점수 반올림과 상태 판정을 수행한다. */
    public static WorkspaceSummaryResponse from(Workspace workspace) {
        Double raw = workspace.getCleanScore();
        Integer displayScore = (raw == null) ? null : (int) Math.round(raw);
        WorkspaceStatus status = (displayScore == null) ? null : WorkspaceStatus.fromScore(displayScore);
        return new WorkspaceSummaryResponse(
            workspace.getWorkspaceId(),
            workspace.getName(),
            workspace.getAddress(),
            workspace.getCategory(),
            workspace.getDistrict(),
            workspace.getLatitude(),
            workspace.getLongitude(),
            displayScore,
            status
        );
    }
}
