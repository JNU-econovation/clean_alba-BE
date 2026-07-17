package com.cleanmap.clean_alba_backend.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TimeSlot {
    MORNING("morning"),
    AFTERNOON("afternoon"),
    NIGHT("night");

    private final String value;

    TimeSlot(String value) {
        this.value = value;
    }

    @JsonCreator
    public static TimeSlot fromJson(String value) {
        return switch (value) {
            case "morning" -> MORNING;
            case "afternoon" -> AFTERNOON;
            case "night" -> NIGHT;
            default -> throw new IllegalArgumentException("timeSlot must be morning, afternoon, or night.");
        };
    }

    @JsonValue
    public String value() {
        return value;
    }
}
