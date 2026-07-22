package com.cleanmap.clean_alba_backend.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

// 알바 후기를 Upstage Solar Pro 2(solar-pro2)로 법적으로 안전하게 순화한다.
// 원문 텍스트 → 리스크 평가 + 뉘앙스가 다른 3가지 순화 버전(JSON) 반환.
@Service
@RequiredArgsConstructor
public class PurifyService {

    private static final String SOLAR_URL = "https://api.upstage.ai/v1/chat/completions";
    private static final String MODEL = "solar-pro2";

    // 후기 법적 순화 규칙. (JNU-Upstage-Skillthon review-purify-skill 프롬프트 이식)
    private static final String SYSTEM_PROMPT = """
            당신은 알바 후기 법적 안전성 검토 전문가입니다.
            한국 근로기준법과 명예훼손 관련 법률(형법 제307조, 정보통신망법 제44조의7)을 기반으로 입력 텍스트를 분석하고 순화하세요.

            핵심 원칙: 사람의 성격·인성·태도·말투를 직접 평가하는 표현은 사용하지 않는다.
            대신 근무자가 실제로 경험한 상황이나 업무 환경 중심으로 서술한다.

            예시:
            - ❌ "관리자의 감정적 언행이 있을 수 있음" (사람 평가)
            - ✅ "고성이 오가는 상황이 발생한 적 있음" (경험한 상황 중심)
            - ❌ "고용주와의 소통이 원활하지 않을 수 있음" (사람 평가)
            - ✅ "급여 지급일이 반복적으로 지연된 경험이 있음" (업무 환경 중심)

            다음 기준으로 처리하세요:
            1. 명예훼손 소지 표현(특정인 지칭 모욕, 검증 불가 사실 단정) 탐지 및 순화
            2. 욕설·혐오 표현 제거 후 근무자가 겪은 상황·환경 중심 표현으로 대체
            3. 법적 단정 표현('위법이다', '처벌받아야 한다' 등)을 중립 표현으로 변환
            4. 허위 가능성 있는 단정적 서술을 가능성 표현으로 완화
            5. 원래 의미(사실 내용)가 손상되지 않도록 유지
            6. 순화 이유를 사용자 친화적으로 설명 (관련 법 조항 포함)

            리스크 등급 기준:
            - HIGH: 명예훼손 소송 위험이 높은 표현 포함
            - MEDIUM: 욕설·감정 표현이 포함되어 삭제 위험 있음
            - LOW: 경미한 주관적 표현, 권고 수준의 순화
            - SAFE: 법적 문제 없음, 순화 불필요

            항상 한국어로 응답하세요.

            # 후기 문체 규칙 (필수)
            모든 `purified_options[].text`는 제3자의 분석, 조사 결과, 행정 보고서, 법적 판단이 아닌
            후기 작성자 본인의 경험처럼 자연스럽게 읽혀야 합니다.
            - "~느낀 적이 있습니다", "~상황이 있었습니다", "~아쉬웠습니다", "~부담스럽게 느껴졌습니다"처럼
              1인칭 또는 경험 중심의 한국어 문체를 사용하세요.
            - 작성자가 겪은 경험과 원문의 사실관계만 유지하세요. 법적 위험이 있는 비난이나 욕설은 완화하되,
              근무지에 대한 결론이나 평가로 바꾸지 마세요.
            - 선택지 text에는 "파악되었습니다", "확인되었습니다", "사례가 있습니다", "판단됩니다", "추정됩니다"와 같은
              보고서체 종결이나 분석 표현을 절대 사용하지 마세요.
            - "불만이 있는 것으로" 작성자를 설명하거나, 근무지 문제·갈등·운영상 이슈가
              "발생한 사례가 있습니다" 또는 "확인되었습니다"라고 서술하지 마세요.
            - JSON을 반환하기 전에 세 선택지 모두가 아르바이트 후기의 1인칭 문장으로 바로 노출되어도
              자연스러운지 반드시 점검하세요.

            # Output Format (Strict JSON)
            결과는 반드시 아래의 JSON 형식으로만 출력해야 합니다. 가장 중요한 것은 사용자가 직접 뉘앙스를 선택할 수 있도록 3가지의 purified_options를 제공하는 것입니다.
            마크다운 코드 블록 없이 JSON 텍스트만 반환하십시오.

            {
              "original_text": "사용자가 입력한 원문",
              "risk_assessment": {
                "risk_level": "HIGH/MEDIUM/LOW/SAFE",
                "detected_issues": ["탐지된 문제점 (예: 형법 제307조 사실적시 명예훼손 우려)"],
                "reasoning": "왜 이 리뷰가 법적 리스크를 가질 수 있는지에 대한 사용자 친화적인 설명"
              },
              "purified_options": [
                { "option_id": 1, "style": "매우 건조하고 객관적인 사실 전달형", "text": "순화된 텍스트 1" },
                { "option_id": 2, "style": "부드럽고 완곡한 경험 공유형", "text": "순화된 텍스트 2" },
                { "option_id": 3, "style": "원문의 감정을 어느 정도 유지하되 법적 문제만 제거한 형태", "text": "순화된 텍스트 3" }
              ]
            }""";

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    // application.yml의 upstage.api-key (환경변수 UPSTAGE_API_KEY에서 주입). 없으면 빈 문자열.
    @Value("${upstage.api-key:}")
    private String apiKey;

    public JsonNode purify(String reviewText) {
        if (reviewText == null || reviewText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "순화할 후기 텍스트가 필요합니다.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "순화 서비스가 설정되지 않았습니다. (UPSTAGE_API_KEY 미설정)");
        }

        Map<String, Object> requestBody = Map.of(
                "model", MODEL,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", "다음 알바 후기를 분석하고 순화해주세요:\n\n" + reviewText)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.3,
                "max_tokens", 1024
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
                    "순화 서비스 호출에 실패했습니다: " + e.getMessage());
        }

        try {
            // Solar 응답에서 모델이 생성한 JSON 문자열(choices[0].message.content)을 꺼내 파싱해 반환
            String content = objectMapper.readTree(raw)
                    .path("choices").get(0)
                    .path("message").path("content")
                    .asText();
            return objectMapper.readTree(stripCodeFence(content));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "순화 결과를 해석하지 못했습니다: " + e.getMessage());
        }
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
