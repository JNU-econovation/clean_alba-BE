package com.cleanmap.clean_alba_backend.domain;

// 사업장 등급. cleanScore로부터 파생되며 DB에 저장하지 않는다.
public enum WorkspaceStatus {
    EXCELLENT(80, null),   // 80 이상 (우수, 🟢)
    NORMAL(60, 80),        // 60 이상 ~ 80 미만 (보통, 🟡)
    CAUTION(40, 60),       // 40 이상 ~ 60 미만 (주의, 🟠)
    DANGER(null, 40);      // 40 미만 (위험, 🔴)

    private final Integer minScore;          // 하한(포함). null이면 하한 없음
    private final Integer maxScoreExclusive; // 상한(미포함). null이면 상한 없음

    WorkspaceStatus(Integer minScore, Integer maxScoreExclusive) {
        this.minScore = minScore;
        this.maxScoreExclusive = maxScoreExclusive;
    }

    // cleanScore로 등급 계산
    public static WorkspaceStatus fromScore(int cleanScore) {
        if (cleanScore >= 80) return EXCELLENT;
        if (cleanScore >= 60) return NORMAL;
        if (cleanScore >= 40) return CAUTION;
        return DANGER;
    }

    public Double getMinStoredScore() {
        return minScore == null ? null : minScore - 0.5;
    }

    public Double getMaxStoredScoreExclusive() {
        return maxScoreExclusive == null ? null : maxScoreExclusive - 0.5;
    }
}
