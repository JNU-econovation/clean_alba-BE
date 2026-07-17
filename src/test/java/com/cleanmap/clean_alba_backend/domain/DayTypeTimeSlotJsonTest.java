package com.cleanmap.clean_alba_backend.domain;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DayTypeTimeSlotJsonTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void serializesAndDeserializesDayTypeAsLowercase() throws Exception {
        assertEquals("\"weekday\"", objectMapper.writeValueAsString(DayType.WEEKDAY));
        assertEquals(DayType.WEEKEND, objectMapper.readValue("\"weekend\"", DayType.class));
    }

    @Test
    void serializesAndDeserializesTimeSlotAsLowercase() throws Exception {
        assertEquals("\"morning\"", objectMapper.writeValueAsString(TimeSlot.MORNING));
        assertEquals(TimeSlot.NIGHT, objectMapper.readValue("\"night\"", TimeSlot.class));
    }
}
