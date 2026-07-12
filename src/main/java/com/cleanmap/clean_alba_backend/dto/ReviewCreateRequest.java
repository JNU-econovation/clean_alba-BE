package com.cleanmap.clean_alba_backend.dto;

public record ReviewCreateRequest(
        Boolean contractViolation,
        Boolean minimumWageViolation,
        Boolean weeklyAllowanceViolation,
        Boolean breakTimeViolation,
        Boolean wageDelayViolation,
        Boolean scheduleChangeViolation,
        Boolean substituteCoercionViolation,
        Boolean overtimePayViolation,
        Integer coworkerCount,
        String content
) {
    public boolean hasAllChecklistAnswers() {
        return contractViolation != null
                && minimumWageViolation != null
                && weeklyAllowanceViolation != null
                && breakTimeViolation != null
                && wageDelayViolation != null
                && scheduleChangeViolation != null
                && substituteCoercionViolation != null
                && overtimePayViolation != null;
    }
}
