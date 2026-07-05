package com.cleanmap.clean_alba_backend.repository;

import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // 클린지수 계산용: 특정 사업장의 승인된 리뷰만 조회
    List<Review> findByWorkspace_WorkspaceIdAndStatus(Long workspaceId, ReviewStatus status);
}
