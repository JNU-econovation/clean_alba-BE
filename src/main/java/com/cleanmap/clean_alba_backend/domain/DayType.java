package com.cleanmap.clean_alba_backend.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DayType {
    WEEKDAY("weekday"),
    WEEKEND("weekend");

    private final String value;

    DayType(String value) {
        this.value = value;
    }

    @JsonCreator
    public static DayType fromJson(String value) {
        return switch (value) {
            case "weekday" -> WEEKDAY;
            case "weekend" -> WEEKEND;
            default -> throw new IllegalArgumentException("dayType must be weekday or weekend.");
        };
    }

    @JsonValue
    public String value() {
        return value;
    }
}
