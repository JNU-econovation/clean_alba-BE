package com.cleanmap.clean_alba_backend.repository;

import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    List<Workspace> findAllByOrderByCleanScoreDescWorkspaceIdAsc();

    List<Workspace> findByStatusOrderByCleanScoreDescWorkspaceIdAsc(WorkspaceStatus status);
}
