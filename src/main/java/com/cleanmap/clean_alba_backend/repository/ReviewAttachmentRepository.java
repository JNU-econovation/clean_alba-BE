package com.cleanmap.clean_alba_backend.repository;

import com.cleanmap.clean_alba_backend.domain.ReviewAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewAttachmentRepository extends JpaRepository<ReviewAttachment, Long> {
    long countByReview_ReviewId(Long reviewId);

    long countByReview_AuthorEmail(String authorEmail);

    @Query("SELECT COALESCE(SUM(a.size), 0) FROM ReviewAttachment a WHERE a.review.authorEmail = :authorEmail")
    long totalSizeByAuthorEmail(@Param("authorEmail") String authorEmail);
}
