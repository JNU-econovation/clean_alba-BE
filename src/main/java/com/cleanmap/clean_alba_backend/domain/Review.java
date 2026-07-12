package com.cleanmap.clean_alba_backend.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 근무 경험과 8개 위반 여부를 기록하는 리뷰 엔티티다.
 * 승인된 리뷰의 {@link #score()}만 사업장의 클린지수 계산에 사용된다.
 */
@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reviewId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    // ── 클린지수 산정용 객관식 체크리스트 8개 (true = 위반 경험 있음, 각 12.5점 감점) ──
    @Column(nullable = false)
    private boolean contractViolation;          // 근로계약서 미작성

    @Column(nullable = false)
    private boolean minimumWageViolation;       // 최저임금 미준수

    @Column(nullable = false)
    private boolean weeklyAllowanceViolation;   // 주휴수당 미지급

    @Column(nullable = false)
    private boolean breakTimeViolation;         // 휴게시간 부족

    @Column(nullable = false)
    private boolean wageDelayViolation;         // 급여 지급 지연

    @Column(nullable = false)
    private boolean scheduleChangeViolation;    // 사전 협의 없는 스케줄 변경

    @Column(nullable = false)
    private boolean substituteCoercionViolation; // 반복적 대타 요구 및 강요

    @Column(nullable = false)
    private boolean overtimePayViolation;       // 초과근무 급여 미지급

    // ── 점수에 반영되지 않는 별도 정보 ──
    private Integer coworkerCount;              // 동시간대 근무자 수 (주관식)

    @Column(columnDefinition = "TEXT")
    private String content;                    // 주관식 자유 후기

    // ── 메타 ──
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status = ReviewStatus.PENDING;

    private String authorEmail;                // 작성자 (카카오 이메일)

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // 이 리뷰 1건의 점수: 100 - (위반 항목 수 × 12.5)
    public double score() {
        return 100.0 - 12.5 * countViolations();
    }

    private int countViolations() {
        int count = 0;
        if (contractViolation) count++;
        if (minimumWageViolation) count++;
        if (weeklyAllowanceViolation) count++;
        if (breakTimeViolation) count++;
        if (wageDelayViolation) count++;
        if (scheduleChangeViolation) count++;
        if (substituteCoercionViolation) count++;
        if (overtimePayViolation) count++;
        return count;
    }
}
