package com.cleanmap.clean_alba_backend.dto;

public record ChecklistStatResponse(
        String item,
        long compliantCount,
        long violationCount
) {
}
