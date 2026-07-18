package com.cleanmap.clean_alba_backend.service;

import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.ReviewAttachment;
import com.cleanmap.clean_alba_backend.domain.ReviewStatus;
import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.domain.WorkspaceStatus;
import com.cleanmap.clean_alba_backend.dto.AdminReviewResponse;
import com.cleanmap.clean_alba_backend.dto.AdminReviewAttachmentResponse;
import com.cleanmap.clean_alba_backend.dto.AdminStatsResponse;
import com.cleanmap.clean_alba_backend.dto.PagedResponse;
import com.cleanmap.clean_alba_backend.dto.ReviewStatusUpdateResponse;
import com.cleanmap.clean_alba_backend.repository.ReviewRepository;
import com.cleanmap.clean_alba_backend.repository.ReviewAttachmentRepository;
import com.cleanmap.clean_alba_backend.repository.WorkspaceRepository;
import com.cleanmap.clean_alba_backend.storage.AttachmentStorage;
import com.cleanmap.clean_alba_backend.storage.AttachmentStorageException;
import com.cleanmap.clean_alba_backend.storage.AttachmentStorageObjectNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewAttachmentRepository reviewAttachmentRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AttachmentStorage attachmentStorage;

    @Transactional(readOnly = true)
    public PagedResponse<AdminReviewResponse> getReviews(String statusValue, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page는 0 이상, size는 1~50이어야 합니다.");
        }
        ReviewStatus status = parseStatus(statusValue);
        return PagedResponse.from(
                reviewRepository.findByStatusOrderByCreatedAtDescReviewIdDesc(
                        status, PageRequest.of(page, size)
                ),
                AdminReviewResponse::from
        );
    }

    @Transactional(readOnly = true)
    public AdminReviewResponse getReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found."));
        review.getWorkspace().getName();
        List<AdminReviewAttachmentResponse> attachments = reviewAttachmentRepository
                .findByReview_ReviewIdOrderByAttachmentIdAsc(reviewId)
                .stream()
                .map(AdminReviewAttachmentResponse::from)
                .toList();
        return AdminReviewResponse.from(review, attachments);
    }

    @Transactional(readOnly = true)
    public AttachmentDownload openAttachment(Long reviewId, Long attachmentId) {
        ReviewAttachment attachment = reviewAttachmentRepository.findByAttachmentIdAndReview_ReviewId(attachmentId, reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found."));
        if (attachment.getStorageKey() == null || attachment.getStorageKey().isBlank()) {
            if (attachment.getContent() == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored attachment not found.");
            }
            return new AttachmentDownload(attachment, new ByteArrayInputStream(attachment.getContent()));
        }
        try {
            return new AttachmentDownload(attachment, attachmentStorage.load(attachment.getStorageKey()));
        } catch (AttachmentStorageObjectNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored attachment not found.");
        } catch (AttachmentStorageException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "인증자료를 불러올 수 없습니다.");
        }
    }

    public record AttachmentDownload(ReviewAttachment attachment, InputStream content) {
    }

    @Transactional
    public ReviewStatusUpdateResponse updateStatus(Long reviewId, ReviewStatus newStatus) {
        if (newStatus == null || newStatus == ReviewStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "승인 또는 반려 상태를 선택해주세요.");
        }
        Review review = reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found."));
        Workspace workspace = workspaceRepository.findByIdForUpdate(review.getWorkspace().getWorkspaceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found."));
        try {
            review.moderate(newStatus);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
        reviewRepository.saveAndFlush(review);

        Double cleanScore = workspace.getCleanScore();
        if (newStatus == ReviewStatus.APPROVED) {
            List<Review> approvedReviews = reviewRepository.findByWorkspace_WorkspaceIdAndStatus(
                    workspace.getWorkspaceId(), ReviewStatus.APPROVED
            );
            cleanScore = approvedReviews.stream().mapToDouble(Review::score).average().orElseThrow();
            workspace.updateCleanScore(cleanScore);
        }

        Integer displayScore = cleanScore == null ? null : (int) Math.round(cleanScore);
        WorkspaceStatus workspaceStatus = displayScore == null ? null : WorkspaceStatus.fromScore(displayScore);
        return new ReviewStatusUpdateResponse(reviewId, review.getStatus(), displayScore, workspaceStatus);
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        return new AdminStatsResponse(
                reviewRepository.count(),
                reviewRepository.countByStatus(ReviewStatus.PENDING),
                reviewRepository.countByStatus(ReviewStatus.APPROVED),
                reviewRepository.countByStatus(ReviewStatus.REJECTED),
                workspaceRepository.count()
        );
    }

    private ReviewStatus parseStatus(String statusValue) {
        String normalized = statusValue == null || statusValue.isBlank()
                ? ReviewStatus.PENDING.name()
                : statusValue.trim().toUpperCase(Locale.ROOT);
        try {
            return ReviewStatus.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "알 수 없는 리뷰 상태입니다.");
        }
    }
}
