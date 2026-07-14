package com.cleanmap.clean_alba_backend.dto;

import java.util.List;

// 자연어 검색 응답. interpreted는 질의를 어떻게 이해했는지(프론트에서 검색 조건 칩으로 노출),
// results는 그 조건으로 DB를 검색한 실제 사업장 목록이다.
public record WorkspaceNlSearchResponse(
    WorkspaceSearchFilter interpreted,
    List<WorkspaceListResponse> results
) {
}
