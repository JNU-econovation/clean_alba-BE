package com.cleanmap.clean_alba_backend.repository;

import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    @Query("""
            SELECT w FROM Workspace w
            WHERE (:status IS NULL OR w.status = :status)
              AND (:keyword IS NULL
                   OR w.name LIKE CONCAT('%', :keyword, '%')
                   OR w.address LIKE CONCAT('%', :keyword, '%'))
            ORDER BY w.cleanScore DESC, w.workspaceId ASC
            """)
    List<Workspace> search(@Param("status") WorkspaceStatus status,
                           @Param("keyword") String keyword);
}
