package com.cleanmap.clean_alba_backend.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExistingDtoJsonCharacterizationTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void workspaceCreateRequestKeepsItsExistingJsonShape() throws Exception {
        String json = """
                {
                  "name": "Cafe Clean",
                  "address": "77 Campus-ro",
                  "category": "CAFE",
                  "district": "Buk-gu",
                  "latitude": 35.1761,
                  "longitude": 126.9058
                }
                """;

        WorkspaceCreateRequest request = objectMapper.readValue(json, WorkspaceCreateRequest.class);

        assertEquals("Cafe Clean", request.name());
        assertEquals(new BigDecimal("35.1761"), request.latitude());
        assertEquals(
                "{\"name\":\"Cafe Clean\",\"address\":\"77 Campus-ro\",\"category\":\"CAFE\","
                        + "\"district\":\"Buk-gu\",\"latitude\":35.1761,\"longitude\":126.9058}",
                objectMapper.writeValueAsString(request));
    }
}
