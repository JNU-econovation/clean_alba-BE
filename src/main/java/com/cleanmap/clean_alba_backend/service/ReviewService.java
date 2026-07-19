package com.cleanmap.clean_alba_backend.service;

import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.ReviewAttachment;
import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.dto.ReviewAttachmentResponse;
import com.cleanmap.clean_alba_backend.dto.ReviewCreateRequest;
import com.cleanmap.clean_alba_backend.dto.ReviewCreateResponse;
import com.cleanmap.clean_alba_backend.dto.ReviewResponse;
import com.cleanmap.clean_alba_backend.repository.ReviewAttachmentRepository;
import com.cleanmap.clean_alba_backend.repository.ReviewRepository;
import com.cleanmap.clean_alba_backend.repository.WorkspaceRepository;
import com.cleanmap.clean_alba_backend.storage.AttachmentStorage;
import com.cleanmap.clean_alba_backend.storage.AttachmentStorageException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private static final long MAX_ATTACHMENT_SIZE = 10L * 1024 * 1024;
    private static final long MAX_ATTACHMENTS_PER_REVIEW = 5;
    private static final long MAX_ATTACHMENTS_PER_USER = 20;
    private static final long MAX_ATTACHMENT_BYTES_PER_USER = 50L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "pdf");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "application/pdf"
    );

    private final ReviewRepository reviewRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ReviewAttachmentRepository reviewAttachmentRepository;
    private final AttachmentStorage attachmentStorage;

    @Transactional
    public ReviewCreateResponse create(Long workspaceId, ReviewCreateRequest request, String authorEmail) {
        if (request == null || !request.hasRequiredFields()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "체크리스트와 근무자 수를 확인해주세요.");
        }
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found."));
        Review review = reviewRepository.save(new Review(workspace, request, authorEmail));
        return ReviewCreateResponse.from(review);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getMyReviews(List<String> authorKeys) {
        return reviewRepository.findByAuthorEmailInOrderByCreatedAtDescReviewIdDesc(authorKeys)
                .stream()
                .map(ReviewResponse::from)
                .toList();
    }

    @Transactional
    public synchronized ReviewAttachmentResponse addAttachment(
            Long reviewId,
            MultipartFile file,
            AuthService.AuthenticatedUser user
    ) {
        Review review = reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found."));
        // 안정 키(kakao:{kakaoId})와 레거시 email 키 어느 쪽으로 저장됐든 본인 리뷰로 인정한다
        String reviewAuthor = review.getAuthorEmail();
        boolean isAuthor = reviewAuthor != null && user.authorKeyCandidates().contains(reviewAuthor);
        if (!isAuthor && !"ADMIN".equals(user.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 리뷰에만 첨부할 수 있습니다.");
        }
        if (reviewAttachmentRepository.countByReview_ReviewId(reviewId) >= MAX_ATTACHMENTS_PER_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "리뷰당 첨부파일은 5개까지 등록할 수 있습니다.");
        }
        String authorEmail = review.getAuthorEmail();
        if (reviewAttachmentRepository.countByReview_AuthorEmail(authorEmail) >= MAX_ATTACHMENTS_PER_USER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "사용자당 첨부파일은 20개까지 등록할 수 있습니다.");
        }
        validateAttachmentMetadata(file);
        try {
            byte[] content = file.getBytes();
            validateAttachmentSignature(file, content);
            long storedBytes = reviewAttachmentRepository.totalSizeByAuthorEmail(authorEmail);
            if (storedBytes + content.length > MAX_ATTACHMENT_BYTES_PER_USER) {
                throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "사용자 첨부파일은 총 50MB까지 등록할 수 있습니다.");
            }
            String safeName = Paths.get(file.getOriginalFilename()).getFileName().toString();
            String storageKey;
            try {
                storageKey = attachmentStorage.store(reviewId, safeName, file.getContentType(), content);
            } catch (AttachmentStorageException exception) {
                log.error(
                        "인증자료 업로드 실패: reviewId={}, filename={}, contentType={}, size={}",
                        reviewId,
                        file.getOriginalFilename(),
                        file.getContentType(),
                        file.getSize(),
                        exception
                    );
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "인증자료 저장소에 업로드할 수 없습니다.");
            }
            ReviewAttachment attachment;
            try {
                attachment = reviewAttachmentRepository.saveAndFlush(new ReviewAttachment(
                        review, safeName, file.getContentType(), content.length, storageKey
                ));
            } catch (RuntimeException exception) {
                deleteStoredObject(reviewId, storageKey);
                throw exception;
            }
            return new ReviewAttachmentResponse(
                    attachment.getAttachmentId(),
                    reviewId,
                    attachment.getOriginalFileName(),
                    attachment.getContentType(),
                    attachment.getSize()
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "첨부파일을 읽을 수 없습니다.");
        }
    }

    private void validateAttachmentMetadata(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "첨부파일이 필요합니다.");
        }
        if (file.getSize() > MAX_ATTACHMENT_SIZE) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "첨부파일은 10MB 이하여야 합니다.");
        }
        String name = file.getOriginalFilename().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1);
        if (!ALLOWED_EXTENSIONS.contains(extension)
                || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jpg, jpeg, png, pdf 파일만 첨부할 수 있습니다.");
        }
    }

    private void validateAttachmentSignature(MultipartFile file, byte[] content) {
        String name = file.getOriginalFilename().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1);
        if (!hasValidSignature(extension, content)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jpg, jpeg, png, pdf 파일만 첨부할 수 있습니다.");
        }
    }

    private void deleteStoredObject(Long reviewId, String storageKey) {
        try {
            attachmentStorage.delete(storageKey);
        } catch (AttachmentStorageException exception) {
            log.warn("Failed to clean up an orphaned attachment object for review {}", reviewId);
        }
    }

    private boolean hasValidSignature(String extension, byte[] content) {
        return switch (extension) {
            case "jpg", "jpeg" -> startsWith(content, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "pdf" -> startsWith(content, 0x25, 0x50, 0x44, 0x46, 0x2D);
            default -> false;
        };
    }

    private boolean startsWith(byte[] content, int... signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if ((content[index] & 0xFF) != signature[index]) {
                return false;
            }
        }
        return true;
    }
}
