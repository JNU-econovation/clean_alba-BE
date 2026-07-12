package com.cleanmap.clean_alba_backend.dto;

// 카카오 장소 resolve 결과. created=true면 신규 생성, false면 기존 사업장 재사용.
public record WorkspaceResolveResponse(
    Long workspaceId,
    boolean created
) {
}
