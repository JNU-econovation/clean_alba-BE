package com.cleanmap.clean_alba_backend.domain;

// 리뷰 검수 상태. 승인(APPROVED)된 리뷰만 클린지수 계산에 반영된다.
public enum ReviewStatus {
    PENDING,   // 검수 대기
    APPROVED,  // 승인 (클린지수 집계 대상)
    REJECTED   // 반려
}
