package com.cleanmap.clean_alba_backend.dto;

import java.math.BigDecimal;

// 사업장 등록 요청 본문. cleanScore는 리뷰로부터 산출되므로 받지 않는다(등록 시 null).
public record WorkspaceCreateRequest(
    String name,
    String address,
    String category,
    String district,
    BigDecimal latitude,
    BigDecimal longitude
) {
}
