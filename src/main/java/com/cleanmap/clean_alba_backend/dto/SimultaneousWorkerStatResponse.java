package com.cleanmap.clean_alba_backend.dto;

import com.cleanmap.clean_alba_backend.domain.DayType;
import com.cleanmap.clean_alba_backend.domain.TimeSlot;

public record SimultaneousWorkerStatResponse(
        DayType dayType,
        TimeSlot timeSlot,
        double averageCoworkerCount,
        long reviewCount
) {
}
