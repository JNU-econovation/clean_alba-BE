package com.cleanmap.clean_alba_backend.service;

import com.cleanmap.clean_alba_backend.domain.DayType;
import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.ReviewAttachment;
import com.cleanmap.clean_alba_backend.domain.TimeSlot;
import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.dto.ReviewCreateRequest;
import com.cleanmap.clean_alba_backend.repository.ReviewAttachmentRepository;
import com.cleanmap.clean_alba_backend.repository.ReviewRepository;
import com.cleanmap.clean_alba_backend.repository.WorkspaceRepository;
import com.cleanmap.clean_alba_backend.storage.AttachmentStorage;
import com.cleanmap.clean_alba_backend.storage.AttachmentStorageObjectNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReviewAttachmentStorageTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private ReviewAttachmentRepository reviewAttachmentRepository;
    @Mock
    private AttachmentStorage attachmentStorage;

    @Test
    void returnsNotFoundWhenStoredObjectIsMissing() {
        AdminReviewService service = new AdminReviewService(
                reviewRepository, reviewAttachmentRepository, workspaceRepository, attachmentStorage);
        Workspace workspace = new Workspace("테스트", "주소", "카페", null,
                new BigDecimal("35.1000000"), new BigDecimal("126.1000000"));
        Review review = new Review(workspace, new ReviewCreateRequest(
                false, false, false, false, false, false, false, false, 1, null,
                DayType.WEEKDAY, TimeSlot.MORNING), "kakao:1");
        ReviewAttachment attachment = new ReviewAttachment(
                review, "proof.pdf", "application/pdf", 8, "reviews/1/missing-proof.pdf");
        when(reviewAttachmentRepository.findByAttachmentIdAndReview_ReviewId(1L, 1L))
                .thenReturn(Optional.of(attachment));
        when(attachmentStorage.load("reviews/1/missing-proof.pdf"))
                .thenThrow(new AttachmentStorageObjectNotFoundException());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.openAttachment(1L, 1L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}
