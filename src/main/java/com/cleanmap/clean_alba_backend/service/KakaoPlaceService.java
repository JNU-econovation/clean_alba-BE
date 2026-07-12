package com.cleanmap.clean_alba_backend.service;

import com.cleanmap.clean_alba_backend.dto.KakaoLocalSearchResponse;
import com.cleanmap.clean_alba_backend.dto.KakaoPlace;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

// 카카오 로컬 API로 장소를 키워드 검색한다. (신규 장소 후보 조회용)
@Service
public class KakaoPlaceService {

    // 카카오 REST API 키(= kakao.client-id). 로컬 API는 "KakaoAK {키}" 헤더를 요구한다.
    @Value("${kakao.client-id}")
    private String restApiKey;

    public List<KakaoPlace> search(String keyword) {
        RestTemplate rt = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "KakaoAK " + restApiKey);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        URI uri = UriComponentsBuilder
                .fromUriString("https://dapi.kakao.com/v2/local/search/keyword.json")
                .queryParam("query", keyword)
                .queryParam("size", 15)
                .build()
                .encode()
                .toUri();

        KakaoLocalSearchResponse body;
        try {
            ResponseEntity<KakaoLocalSearchResponse> response =
                    rt.exchange(uri, HttpMethod.GET, request, KakaoLocalSearchResponse.class);
            body = response.getBody();
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "카카오 장소 검색에 실패했습니다.");
        }

        if (body == null || body.documents() == null) {
            return List.of();
        }

        return body.documents().stream()
                .map(this::toKakaoPlace)
                .toList();
    }

    private KakaoPlace toKakaoPlace(KakaoLocalSearchResponse.Document d) {
        String address = (d.roadAddressName() != null && !d.roadAddressName().isBlank())
                ? d.roadAddressName()
                : d.addressName();
        return new KakaoPlace(
                d.id(),
                d.placeName(),
                address,
                d.categoryGroupName(),
                new BigDecimal(d.y()),   // 위도
                new BigDecimal(d.x())    // 경도
        );
    }
}
