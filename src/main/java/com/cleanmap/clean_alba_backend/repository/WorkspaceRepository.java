package com.cleanmap.clean_alba_backend.repository;

import com.cleanmap.clean_alba_backend.domain.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

/**
 * 사업장 저장과 지도 목록 검색을 담당한다.
 * 검색 결과는 클린지수가 있는 사업장만 포함하며 점수가 높은 순으로 정렬된다.
 */
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    /** 상태의 점수 범위와 자유 검색어를 한 쿼리에서 선택적으로 적용한다. */
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
    List<Workspace> search(@Param("minScore") Double minScore,
                           @Param("maxScore") Double maxScore,
                           @Param("keyword") String keyword);

    @Query("""
            SELECT w FROM Workspace w
            WHERE w.name LIKE CONCAT('%', :keyword, '%')
               OR w.address LIKE CONCAT('%', :keyword, '%')
               OR w.category LIKE CONCAT('%', :keyword, '%')
               OR w.district LIKE CONCAT('%', :keyword, '%')
            ORDER BY w.name ASC, w.workspaceId ASC
            """)
    List<Workspace> searchAllByKeyword(@Param("keyword") String keyword);

    /** 자연어 해석 결과(점수 범위·상권·업종·키워드)를 AND로 결합해 검색한다. null인 조건은 무시된다. */
    @Query("""
            SELECT w FROM Workspace w
            WHERE w.cleanScore IS NOT NULL
              AND (:minScore IS NULL OR w.cleanScore >= :minScore)
              AND (:maxScore IS NULL OR w.cleanScore < :maxScore)
              AND (:district IS NULL OR w.district LIKE CONCAT('%', :district, '%'))
              AND (:category IS NULL OR w.category LIKE CONCAT('%', :category, '%'))
              AND (:keyword IS NULL
                   OR w.name LIKE CONCAT('%', :keyword, '%')
                   OR w.address LIKE CONCAT('%', :keyword, '%'))
            ORDER BY w.cleanScore DESC, w.workspaceId ASC
            """)
    List<Workspace> searchByFilter(@Param("minScore") Double minScore,
                                   @Param("maxScore") Double maxScore,
                                   @Param("district") String district,
                                   @Param("category") String category,
                                   @Param("keyword") String keyword);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Workspace w WHERE w.workspaceId = :workspaceId")
    Optional<Workspace> findByIdForUpdate(@Param("workspaceId") Long workspaceId);

    // 카카오 장소 기반 중복확인용
    Optional<Workspace> findByKakaoPlaceId(String kakaoPlaceId);

    Optional<Workspace> findByNameAndAddress(String name, String address);
}
