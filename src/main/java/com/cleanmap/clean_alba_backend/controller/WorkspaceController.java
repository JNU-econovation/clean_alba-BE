package com.cleanmap.clean_alba_backend.controller;

import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;
import com.cleanmap.clean_alba_backend.dto.WorkspaceCreateRequest;
import com.cleanmap.clean_alba_backend.dto.WorkspaceListResponse;
import com.cleanmap.clean_alba_backend.dto.WorkspaceSummaryResponse;
import com.cleanmap.clean_alba_backend.service.WorkspaceService;
import com.cleanmap.clean_alba_backend.util.JwtUtil;
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
    private final JwtUtil jwtUtil;

    // POST /workspaces — 신규 사업장 등록 (관리자 전용)
    @PostMapping
    public ResponseEntity<?> createWorkspace(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestBody WorkspaceCreateRequest request
    ) {
        String token = authorizationHeader.replace("Bearer ", "");

        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(401).body("유효하지 않은 토큰입니다.");
        }
        if (!"ADMIN".equals(jwtUtil.getRoleFromToken(token))) {
            return ResponseEntity.status(403).body("관리자만 사업장을 등록할 수 있습니다.");
        }

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

    @GetMapping("/{workspaceId}/summary")
    public WorkspaceSummaryResponse getWorkspaceSummary(
        @PathVariable Long workspaceId
    ) {
        return workspaceService.getWorkspaceSummary(workspaceId);
    }
}
