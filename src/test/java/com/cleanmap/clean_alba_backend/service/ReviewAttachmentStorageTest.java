package com.cleanmap.clean_alba_backend.service;

import com.cleanmap.clean_alba_backend.domain.DayType;
import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.TimeSlot;
import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.dto.ReviewCreateRequest;
import com.cleanmap.clean_alba_backend.repository.ReviewAttachmentRepository;
import com.cleanmap.clean_alba_backend.repository.ReviewRepository;
import com.cleanmap.clean_alba_backend.repository.WorkspaceRepository;
import com.cleanmap.clean_alba_backend.storage.AttachmentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewAttachmentStorageTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private ReviewAttachmentRepository reviewAttachmentRepository;
    @Mock
    private AttachmentStorage attachmentStorage;

    private ReviewService reviewService;
    private Review review;
    private AuthService.AuthenticatedUser author;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(
                reviewRepository, workspaceRepository, reviewAttachmentRepository, attachmentStorage);
        Workspace workspace = new Workspace("테스트", "주소", "카페", null,
                new BigDecimal("35.1000000"), new BigDecimal("126.1000000"));
        ReviewCreateRequest request = new ReviewCreateRequest(
                false, false, false, false, false, false, false, false, 1, null,
                DayType.WEEKDAY, TimeSlot.MORNING);
        review = new Review(workspace, request, "kakao:1");
        author = new AuthService.AuthenticatedUser(null, "USER", "token", 1L);
        when(reviewRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(review));
    }

    @Test
    void storesFileInStorageAndPersistsOnlyMetadata() {
        MockMultipartFile file = pdf("proof.pdf");
        allowUpload();
        when(attachmentStorage.store(eq(1L), eq("proof.pdf"), eq("application/pdf"), any()))
                .thenReturn("reviews/1/object");
        when(reviewAttachmentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        reviewService.addAttachment(1L, file, author);

        ArgumentCaptor<com.cleanmap.clean_alba_backend.domain.ReviewAttachment> attachment =
                ArgumentCaptor.forClass(com.cleanmap.clean_alba_backend.domain.ReviewAttachment.class);
        verify(reviewAttachmentRepository).saveAndFlush(attachment.capture());
        verify(attachmentStorage).store(eq(1L), eq("proof.pdf"), eq("application/pdf"), any());
        org.junit.jupiter.api.Assertions.assertEquals("reviews/1/object", attachment.getValue().getStorageKey());
        org.junit.jupiter.api.Assertions.assertEquals(null, attachment.getValue().getContent());
    }

    @Test
    void rejectsInvalidOrOversizedFilesBeforeStorage() {
        MockMultipartFile invalid = new MockMultipartFile("file", "proof.exe", "application/octet-stream", "bad".getBytes());
        allowUploadWithoutSizeCheck();
        assertThrows(RuntimeException.class, () -> reviewService.addAttachment(1L, invalid, author));

        MultipartFile oversized = org.mockito.Mockito.mock(MultipartFile.class);
        when(oversized.getOriginalFilename()).thenReturn("proof.pdf");
        when(oversized.getSize()).thenReturn(10L * 1024 * 1024 + 1);
        when(oversized.isEmpty()).thenReturn(false);
        assertThrows(RuntimeException.class, () -> reviewService.addAttachment(1L, oversized, author));

        verify(attachmentStorage, never()).store(any(), any(), any(), any());
    }

    @Test
    void rejectsSixthAttachmentBeforeStorage() {
        when(reviewAttachmentRepository.countByReview_ReviewId(1L)).thenReturn(5L);

        assertThrows(RuntimeException.class, () -> reviewService.addAttachment(1L, pdf("proof.pdf"), author));

        verify(attachmentStorage, never()).store(any(), any(), any(), any());
    }

    @Test
    void deletesStoredObjectWhenMetadataPersistenceFails() {
        MockMultipartFile file = pdf("proof.pdf");
        allowUpload();
        when(attachmentStorage.store(eq(1L), eq("proof.pdf"), eq("application/pdf"), any()))
                .thenReturn("reviews/1/orphan");
        when(reviewAttachmentRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("db failure"));

        assertThrows(DataIntegrityViolationException.class, () -> reviewService.addAttachment(1L, file, author));

        verify(attachmentStorage).delete("reviews/1/orphan");
    }

    private MockMultipartFile pdf(String fileName) {
        return new MockMultipartFile("file", fileName, "application/pdf", "%PDF-1.4".getBytes());
    }

    private void allowUploadWithoutSizeCheck() {
        when(reviewAttachmentRepository.countByReview_ReviewId(1L)).thenReturn(0L);
        when(reviewAttachmentRepository.countByReview_AuthorEmail("kakao:1")).thenReturn(0L);
    }

    private void allowUpload() {
        allowUploadWithoutSizeCheck();
        when(reviewAttachmentRepository.totalSizeByAuthorEmail("kakao:1")).thenReturn(0L);
    }
}
