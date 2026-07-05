package com.cleanmap.clean_alba_backend.repository;

import com.cleanmap.clean_alba_backend.domain.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    @Query("""
            SELECT w FROM Workspace w
            WHERE w.cleanScore IS NOT NULL
              AND (:minScore IS NULL OR w.cleanScore >= :minScore)
              AND (:maxScore IS NULL OR w.cleanScore < :maxScore)
              AND (:keyword IS NULL
                   OR w.name LIKE CONCAT('%', :keyword, '%')
                   OR w.address LIKE CONCAT('%', :keyword, '%')
                   OR w.category LIKE CONCAT('%', :keyword, '%')
                   OR w.district LIKE CONCAT('%', :keyword, '%'))
            ORDER BY w.cleanScore DESC, w.workspaceId ASC
            """)
    List<Workspace> search(@Param("minScore") Integer minScore,
                           @Param("maxScore") Integer maxScore,
                           @Param("keyword") String keyword);
}
