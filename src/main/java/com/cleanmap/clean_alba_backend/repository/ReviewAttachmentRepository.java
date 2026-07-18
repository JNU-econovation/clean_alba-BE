package com.cleanmap.clean_alba_backend.repository;

import com.cleanmap.clean_alba_backend.domain.ReviewAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewAttachmentRepository extends JpaRepository<ReviewAttachment, Long> {
    List<ReviewAttachment> findByReview_ReviewIdOrderByAttachmentIdAsc(Long reviewId);

    Optional<ReviewAttachment> findByAttachmentIdAndReview_ReviewId(Long attachmentId, Long reviewId);

    long countByReview_ReviewId(Long reviewId);

    long countByReview_AuthorEmail(String authorEmail);

    @Query("SELECT COALESCE(SUM(a.size), 0) FROM ReviewAttachment a WHERE a.review.authorEmail = :authorEmail")
    long totalSizeByAuthorEmail(@Param("authorEmail") String authorEmail);
}
