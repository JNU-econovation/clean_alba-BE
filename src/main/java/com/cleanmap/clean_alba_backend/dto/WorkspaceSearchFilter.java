package com.cleanmap.clean_alba_backend.dto;

// 자연어 질의("클린점수 60점 넘는 상대 카페")를 Solar가 해석한 구조화된 검색 조건.
// 조건이 없는 항목은 null(= 제한 없음)이다. 점수는 응답에 노출되는 반올림 점수 기준.
public record WorkspaceSearchFilter(
    Integer minScore,   // 이상(포함). 없으면 null
    Integer maxScore,   // 이하(포함). 없으면 null
    String district,    // 상권(상대/예대/정문/후문 등)
    String category,    // 업종(카페/식당/편의점/주점 등)
    String keyword      // 상호명·주소 조각
) {
}
