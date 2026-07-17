package com.cleanmap.clean_alba_backend.service;

import com.cleanmap.clean_alba_backend.dto.KakaoLocalSearchResponse;
import com.cleanmap.clean_alba_backend.dto.KakaoPlace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;

// 카카오 로컬 API로 장소를 키워드 검색한다. (신규 장소 후보 조회용)
@Service
public class KakaoPlaceService {

    private static final Logger log = LoggerFactory.getLogger(KakaoPlaceService.class);

    // 카카오 REST API 키(= kakao.client-id). 로컬 API는 "KakaoAK {키}" 헤더를 요구한다.
    @Value("${kakao.client-id}")
    private String restApiKey;

    private final RestTemplate rt;

    public KakaoPlaceService() {
        this(defaultRestTemplate());
    }

    // 기본 RestTemplate은 타임아웃이 무제한이라, 카카오 연결이 막히면 요청이
    // 프록시(Caddy) 타임아웃까지 매달린다. 빠르게 실패하도록 제한을 둔다.
    private static RestTemplate defaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }

    // 테스트에서 카카오 응답을 스텁하기 위한 생성자.
    KakaoPlaceService(RestTemplate rt) {
        this.rt = rt;
    }

    public List<KakaoPlace> search(String keyword) {
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
        } catch (HttpStatusCodeException exception) {
            // 카카오가 준 상태·본문을 남겨야 키 종류 오류(401), 카카오맵 미활성화(403),
            // 쿼터 초과(429) 같은 원인을 운영 로그에서 구분할 수 있다.
            log.error("카카오 장소 검색 실패: status={} body={}",
                    exception.getStatusCode().value(), exception.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "카카오 장소 검색에 실패했습니다. (카카오 응답 " + exception.getStatusCode().value() + ")");
        } catch (RestClientException exception) {
            log.error("카카오 장소 검색 실패: {}", exception.getMessage());
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
                category(d),
                new BigDecimal(d.y()),   // 위도
                new BigDecimal(d.x())    // 경도
        );
    }

    // category_group_name은 카카오의 15개 대분류에 속하지 않는 장소에서 빈 문자열로 온다.
    // 그대로 두면 resolve가 "장소 정보 부족"으로 400을 내므로 "기타"로 채운다.
    // (세부 분류는 프론트의 카테고리 체계와 맞지 않아 쓰지 않는다.)
    private String category(KakaoLocalSearchResponse.Document d) {
        return (d.categoryGroupName() != null && !d.categoryGroupName().isBlank())
                ? d.categoryGroupName()
                : "기타";
    }
}
