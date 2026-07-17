package com.cleanmap.clean_alba_backend.repository;

import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 리뷰 저장과 사업장별 승인 리뷰 조회를 담당한다. */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // 클린지수 계산용: 특정 사업장의 승인된 리뷰만 조회
    List<Review> findByWorkspace_WorkspaceIdAndStatus(Long workspaceId, ReviewStatus status);

    @EntityGraph(attributePaths = "workspace")
    Page<Review> findByStatusOrderByCreatedAtDescReviewIdDesc(ReviewStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "workspace")
    List<Review> findByAuthorEmailOrderByCreatedAtDescReviewIdDesc(String authorEmail);

    // 내 리뷰 조회용: 안정 키(kakao:{kakaoId})와 레거시 email 키를 함께 조회한다
    @EntityGraph(attributePaths = "workspace")
    List<Review> findByAuthorEmailInOrderByCreatedAtDescReviewIdDesc(Collection<String> authorEmails);

    long countByStatus(ReviewStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Review r JOIN FETCH r.workspace WHERE r.reviewId = :reviewId")
    Optional<Review> findByIdForUpdate(@Param("reviewId") Long reviewId);
}
