package com.cleanmap.clean_alba_backend.service;

import com.cleanmap.clean_alba_backend.dto.WorkspaceSearchFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Solar 응답 → WorkspaceSearchFilter 매핑 검증. 검색어에 없는 조건은 null이어야 한다. */
class NaturalLanguageQueryParserTest {

    private static final String SOLAR_URL = "https://api.upstage.ai/v1/chat/completions";

    @Test
    void parsesScoreDistrictAndCategoryFromQuery() {
        // Given: Solar가 "클린점수 60점 넘는 상대 카페"를 조건으로 해석해 돌려준다
        WorkspaceSearchFilter filter = parseWith(
                "{\"minScore\":60,\"maxScore\":null,\"district\":\"상대\",\"category\":\"카페\",\"keyword\":null}");

        // Then: 조건이 그대로 매핑되고, 언급되지 않은 조건은 null이다
        assertEquals(60, filter.minScore());
        assertEquals("상대", filter.district());
        assertEquals("카페", filter.category());
        assertNull(filter.maxScore());
        assertNull(filter.keyword());
    }

    @Test
    void treatsNullAndBlankFieldsAsNoCondition() {
        // Given: 조건이 하나도 없는 해석 결과(빈 문자열 포함)
        WorkspaceSearchFilter filter = parseWith(
                "{\"minScore\":null,\"maxScore\":null,\"district\":\"\",\"category\":null,\"keyword\":\"  \"}");

        // Then: 빈 문자열도 "조건 없음"(null)으로 취급해 검색을 좁히지 않는다
        assertNull(filter.minScore());
        assertNull(filter.district());
        assertNull(filter.category());
        assertNull(filter.keyword());
    }

    @Test
    void unwrapsMarkdownCodeFence() {
        // Given: 모델이 JSON을 코드펜스로 감싼 경우
        WorkspaceSearchFilter filter = parseWith(
                "```json\\n{\"minScore\":80,\"maxScore\":null,\"district\":null,\"category\":\"식당\",\"keyword\":null}\\n```");

        // Then: 코드펜스를 벗겨내고 해석한다
        assertEquals(80, filter.minScore());
        assertEquals("식당", filter.category());
    }

    private WorkspaceSearchFilter parseWith(String solarContentJson) {
        String solarResponse = "{\"choices\":[{\"message\":{\"content\":\"%s\"}}]}"
                .formatted(solarContentJson.replace("\"", "\\\""));

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(SOLAR_URL))
                .andRespond(withSuccess(solarResponse, MediaType.APPLICATION_JSON));

        NaturalLanguageQueryParser parser =
                new NaturalLanguageQueryParser(JsonMapper.builder().build(), builder.build());
        ReflectionTestUtils.setField(parser, "apiKey", "test-key");

        WorkspaceSearchFilter filter = parser.parse("클린점수 60점 넘는 상대 카페 찾아줘");
        server.verify();
        return filter;
    }
}
