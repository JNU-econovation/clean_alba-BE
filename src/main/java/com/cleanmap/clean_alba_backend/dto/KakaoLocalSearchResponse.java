package com.cleanmap.clean_alba_backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// 카카오 로컬 키워드 검색(https://dapi.kakao.com/v2/local/search/keyword.json) 응답 매핑용.
// 필요한 필드만 뽑고 나머지는 무시한다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoLocalSearchResponse(List<Document> documents) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
        String id,                                          // 카카오 장소 고유 ID
        @JsonProperty("place_name") String placeName,       // 장소명
        @JsonProperty("category_group_name") String categoryGroupName, // 대분류(카페/음식점 등). 15개 그룹 미해당 장소는 빈 문자열
        @JsonProperty("address_name") String addressName,   // 지번 주소
        @JsonProperty("road_address_name") String roadAddressName, // 도로명 주소
        String x,                                           // 경도(longitude)
        String y                                            // 위도(latitude)
    ) {
    }
}
