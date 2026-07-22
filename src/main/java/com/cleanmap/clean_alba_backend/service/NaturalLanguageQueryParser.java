package com.cleanmap.clean_alba_backend.service;

import com.cleanmap.clean_alba_backend.dto.WorkspaceSearchFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

// 자연어 검색어를 Upstage Solar로 구조화된 검색 조건(WorkspaceSearchFilter)으로 변환한다.
// 사업장 검색 자체는 이 결과를 받아 DB가 수행한다(LLM이 사업장을 지어내지 못하도록).
@Service
public class NaturalLanguageQueryParser {

    private static final String SOLAR_URL = "https://api.upstage.ai/v1/chat/completions";
    private static final String MODEL = "solar-pro2";

    private static final String SYSTEM_PROMPT = """
            당신은 아르바이트 사업장 검색어 해석기입니다.
            사용자의 자연어 검색어를 아래 JSON 형식의 검색 조건으로만 변환하세요.

            필드 설명:
            - minScore: 클린지수 하한(이상, 0~100 정수). "60점 넘는", "70점 이상" 등. 없으면 null
            - maxScore: 클린지수 상한(이하, 0~100 정수). "50점 이하" 등. 없으면 null
            - district: 상권. 상대, 예대, 정문, 후문 중 하나. 없으면 null
            - category: 업종. 카페, 식당, 편의점, 주점 등. 없으면 null
            - keyword: 상호명이나 주소 조각(예: "스타벅스"). 없으면 null

            규칙:
            - 검색어에 없는 조건은 반드시 null로 두세요. 추측해서 채우지 마세요.
            - 등급 표현은 점수로 바꾸세요: 우수=80 이상, 보통=60~79, 주의=40~59, 위험=39 이하.
            - "클린한", "괜찮은" 같은 모호한 표현은 minScore=60으로 해석하세요.
            - 마크다운 없이 JSON만 출력하세요.

            # Output Format (Strict JSON)
            {"minScore": null, "maxScore": null, "district": null, "category": null, "keyword": null}
            """;

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Value("${upstage.api-key:}")
    private String apiKey;

    @Autowired
    public NaturalLanguageQueryParser(ObjectMapper objectMapper) {
        this(objectMapper, RestClient.create());
    }

    // 테스트에서 Solar 응답을 스텁하기 위한 생성자.
    NaturalLanguageQueryParser(ObjectMapper objectMapper, RestClient restClient) {
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    public WorkspaceSearchFilter parse(String query) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어가 필요합니다.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "자연어 검색이 설정되지 않았습니다. (UPSTAGE_API_KEY 미설정)");
        }

        Map<String, Object> requestBody = Map.of(
                "model", MODEL,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", query.trim())
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.0,
                "max_tokens", 256
        );

        String raw;
        try {
            raw = restClient.post()
                    .uri(SOLAR_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "자연어 검색 해석에 실패했습니다: " + e.getMessage());
        }

        try {
            String content = objectMapper.readTree(raw)
                    .path("choices").get(0)
                    .path("message").path("content")
                    .asText();
            JsonNode filter = objectMapper.readTree(stripCodeFence(content));
            return new WorkspaceSearchFilter(
                    intOrNull(filter, "minScore"),
                    intOrNull(filter, "maxScore"),
                    textOrNull(filter, "district"),
                    textOrNull(filter, "category"),
                    textOrNull(filter, "keyword")
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "검색어를 해석하지 못했습니다: " + e.getMessage());
        }
    }

    private Integer intOrNull(JsonNode filter, String field) {
        JsonNode value = filter.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private String textOrNull(JsonNode filter, String field) {
        JsonNode value = filter.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isBlank() ? null : text;
    }

    // 모델이 실수로 ```json ... ``` 코드펜스를 감쌌을 때를 대비해 제거한다.
    private String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(json)?", "").trim();
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
            }
        }
        return trimmed;
    }
}
