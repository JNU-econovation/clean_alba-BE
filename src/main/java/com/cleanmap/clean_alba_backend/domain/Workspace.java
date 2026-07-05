package com.cleanmap.clean_alba_backend.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(
    name = "workspaces",
    indexes = @Index(name = "idx_workspace_clean_score", columnList = "clean_score")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long workspaceId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String district;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    // 승인된 리뷰들로부터 산출되는 값(소수 정밀). 리뷰가 없으면 null(지도·목록 미노출).
    @Column
    private Double cleanScore;

    // 사업장 등록용. cleanScore는 리뷰 승인 시 재계산되므로 초기값은 null이다.
    public Workspace(String name, String address, String category, String district,
                     BigDecimal latitude, BigDecimal longitude) {
        this.name = name;
        this.address = address;
        this.category = category;
        this.district = district;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // 리뷰 승인/변경 시 재계산된 클린지수를 반영. null이면 산출 대상 리뷰 없음.
    public void updateCleanScore(Double cleanScore) {
        this.cleanScore = cleanScore;
    }
}