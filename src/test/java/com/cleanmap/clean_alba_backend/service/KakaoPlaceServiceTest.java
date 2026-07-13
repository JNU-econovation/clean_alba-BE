package com.cleanmap.clean_alba_backend.service;

import com.cleanmap.clean_alba_backend.dto.KakaoPlace;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 카카오 로컬 검색 응답 → KakaoPlace 매핑 검증.
 * category_group_name은 카카오의 15개 대분류에 속하지 않는 장소에서 빈 문자열로 오는데,
 * 그대로 두면 통합 검색에는 노출되지만 resolve가 "장소 정보가 부족합니다"(400)로 막힌다.
 */
class KakaoPlaceServiceTest {

    private static final String SEARCH_URL =
            "https://dapi.kakao.com/v2/local/search/keyword.json?query=%EC%9A%A9%EB%B4%89&size=15";

    @Test
    void categoryGroupIsUsedWhenPresent() {
        // Given: 대분류가 정상적으로 내려오는 장소
        List<KakaoPlace> places = searchWith("""
                { "documents": [
                  { "id": "1", "place_name": "스타벅스 전남대후문점", "category_group_name": "카페",
                    "category_name": "음식점 > 카페 > 커피전문점",
                    "address_name": "광주 북구 용봉동 2", "road_address_name": "광주 북구 용봉로 105",
                    "x": "126.9088000", "y": "35.1812000" }
                ] }
                """);

        // Then: 대분류를 그대로 쓴다
        assertEquals("카페", places.get(0).category());
    }

    @Test
    void blankCategoryGroupFallsBackToDefault() {
        // Given: 대분류 없이(빈 문자열) 세부 분류만 내려오는 장소
        List<KakaoPlace> places = searchWith("""
                { "documents": [
                  { "id": "2", "place_name": "용봉호프", "category_group_name": "",
                    "category_name": "음식점 > 술집 > 호프,요리주점",
                    "address_name": "광주 북구 용봉동 1", "road_address_name": "광주 북구 용봉로 112",
                    "x": "126.9100000", "y": "35.1815000" }
                ] }
                """);

        // Then: 세부 분류를 쓰지 않고 "기타"로 채워 resolve가 400으로 막히지 않는다
        assertEquals("기타", places.get(0).category());
    }

    @Test
    void missingCategoryGroupFallsBackToDefault() {
        // Given: 분류 정보가 아예 없는 장소
        List<KakaoPlace> places = searchWith("""
                { "documents": [
                  { "id": "3", "place_name": "무분류 가게",
                    "address_name": "광주 북구 용봉동 3", "road_address_name": "",
                    "x": "126.9000000", "y": "35.1800000" }
                ] }
                """);

        // Then: 빈 값 대신 기본값이 채워진다(주소는 도로명이 비어 지번으로 대체)
        assertEquals("기타", places.get(0).category());
        assertEquals("광주 북구 용봉동 3", places.get(0).address());
    }

    private List<KakaoPlace> searchWith(String kakaoResponseJson) {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(SEARCH_URL))
                .andRespond(withSuccess(kakaoResponseJson, MediaType.APPLICATION_JSON));

        List<KakaoPlace> places = new KakaoPlaceService(restTemplate).search("용봉");
        server.verify();
        return places;
    }
}
