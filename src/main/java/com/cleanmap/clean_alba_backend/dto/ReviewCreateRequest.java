package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.DayType;
import com.cleanmap.clean_alba_backend.domain.TimeSlot;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ReviewCreateRequest(
        @NotNull
        Boolean contractViolation,
        @NotNull
        Boolean minimumWageViolation,
        @NotNull
        @JsonProperty("weeklyHolidayAllowanceViolation")
        @JsonAlias("weeklyAllowanceViolation")
        Boolean weeklyAllowanceViolation,
        @NotNull
        Boolean breakTimeViolation,
        @NotNull
        Boolean wageDelayViolation,
        @NotNull
        Boolean scheduleChangeViolation,
        @NotNull
        @JsonProperty("substituteDemandViolation")
        @JsonAlias("substituteCoercionViolation")
        Boolean substituteCoercionViolation,
        @NotNull
        Boolean overtimePayViolation,
        @NotNull
        @Min(0)
        Integer coworkerCount,
        String content,
        @NotNull
        DayType dayType,
        @NotNull
        TimeSlot timeSlot
) {
    public ReviewCreateRequest(
            Boolean contractViolation, Boolean minimumWageViolation, Boolean weeklyAllowanceViolation,
            Boolean breakTimeViolation, Boolean wageDelayViolation, Boolean scheduleChangeViolation,
            Boolean substituteCoercionViolation, Boolean overtimePayViolation, Integer coworkerCount, String content
    ) {
        this(contractViolation, minimumWageViolation, weeklyAllowanceViolation, breakTimeViolation,
                wageDelayViolation, scheduleChangeViolation, substituteCoercionViolation, overtimePayViolation,
                coworkerCount, content, DayType.WEEKDAY, TimeSlot.MORNING);
    }
    public boolean hasRequiredFields() {
        return contractViolation != null
                && minimumWageViolation != null
                && weeklyAllowanceViolation != null
                && breakTimeViolation != null
                && wageDelayViolation != null
                && scheduleChangeViolation != null
                && substituteCoercionViolation != null
                && overtimePayViolation != null
                && coworkerCount != null
                && coworkerCount >= 0
                && dayType != null
                && timeSlot != null;
    }
}
