# 클린알바맵 백엔드 API 명세

현재 백엔드 코드의 Controller, DTO, 인증 필터를 기준으로 작성한 프론트엔드 연동 계약 문서다.

## 0. 공통 사항

### Base URL

- 로컬: `http://localhost:8080`
- 운영: 배포된 백엔드 HTTPS 도메인

### 요청 형식

- 기본 요청·응답: `application/json`
- 첨부파일 업로드: `multipart/form-data`
- 첨부파일 다운로드: 파일 스트림

### 인증

보호 API는 로그인 시 발급받은 서비스 JWT를 다음 형식으로 전달한다.

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

| 표기 | 의미 |
|---|---|
| 공개 | JWT 없이 호출 가능 |
| USER | 유효한 USER 또는 ADMIN JWT 필요 |
| ADMIN | ADMIN JWT 필요 |

JWT의 기본 만료 시간은 24시간이다. 역할은 카카오 고유 ID와 서버의 `ADMIN_IDS` 설정을 비교해 `USER` 또는 `ADMIN`으로 정한다.

### 인증 오류

JWT 필터에서 거절된 요청은 `text/plain;charset=UTF-8` 본문을 반환한다.

| 상태 | 예시 |
|---|---|
| `401 Unauthorized` | `Bearer 토큰이 필요합니다.` / `유효하지 않은 토큰입니다.` |
| `403 Forbidden` | `관리자 권한이 필요합니다.` |

Controller 또는 Service에서 발생한 오류는 Spring의 오류 응답 형식을 따른다. 주요 상태 코드는 각 API 설명에 기재한다.

### 공통 enum

#### 리뷰 상태 `ReviewStatus`

- `PENDING`: 관리자 검수 대기
- `APPROVED`: 승인, 공개 및 클린지수 집계 대상
- `REJECTED`: 반려

#### 사업장 상태 `WorkspaceStatus`

사업장 상태는 저장된 평균 점수를 반올림한 `cleanScore`로 계산한다.

| 값 | 표시 점수 |
|---|---|
| `EXCELLENT` | 80 이상 |
| `NORMAL` | 60 이상 80 미만 |
| `CAUTION` | 40 이상 60 미만 |
| `DANGER` | 40 미만 |

승인 리뷰가 없으면 `cleanScore`와 `status`는 `null`이다.

#### 후기 감정 `ReviewSentiment`

- `POSITIVE`
- `NEUTRAL`
- `NEGATIVE`

#### 근무 요일·시간대

- `dayType`: `weekday` / `weekend`
- `timeSlot`: `morning` / `afternoon` / `night`

### 클린지수 체크리스트

각 값은 `true`일 때 위반 경험이 있음을 의미한다. 리뷰 한 건의 점수는 `100 - (위반 항목 수 × 12.5)`이며, 사업장 클린지수는 승인 리뷰 점수의 평균이다.

| 요청 필드 | 통계 item | 의미 |
|---|---|---|
| `contractViolation` | `CONTRACT` | 근로계약서 미작성 |
| `minimumWageViolation` | `MINIMUM_WAGE` | 최저임금 미준수 |
| `weeklyHolidayAllowanceViolation` | `WEEKLY_ALLOWANCE` | 주휴수당 미지급 |
| `breakTimeViolation` | `BREAK_TIME` | 휴게시간 부족 |
| `wageDelayViolation` | `WAGE_DELAY` | 급여 지급 지연 |
| `scheduleChangeViolation` | `SCHEDULE_CHANGE` | 사전 협의 없는 일정 변경 |
| `substituteDemandViolation` | `SUBSTITUTE_COERCION` | 반복적인 대타 요구·강요 |
| `overtimePayViolation` | `OVERTIME_PAY` | 초과근무 급여 미지급 |

`weeklyAllowanceViolation`, `substituteCoercionViolation`도 각각 이전 필드명의 alias로 허용한다.

---

## 1. 인증 API

### 1-1. 카카오 로그인 및 JWT 발급

`POST /auth/kakao/callback` · 공개

프론트엔드가 카카오에서 받은 인가 코드를 전달한다. 백엔드는 카카오 토큰 API와 사용자 정보 API를 호출한 뒤 서비스 JWT를 발급한다.

**Request**

```json
{ "code": "kakao-authorization-code" }
```

**Response 200**

```json
{
  "token": "eyJhbGciOi...",
  "userEmail": "user@example.com",
  "nickname": "닉네임",
  "role": "USER"
}
```

카카오 계정의 동의 상태에 따라 `userEmail`과 `nickname`은 `null`일 수 있다. 사용자 식별의 필수값은 카카오 고유 ID다.

**오류**

- `400`: 인가 코드가 비어 있음
- `502`: 카카오 로그인 요청 실패 또는 사용자 정보 확인 실패

### 1-2. JWT 갱신

`POST /auth/refresh` · USER

아직 유효한 JWT를 새 JWT로 교체하고 기존 JWT를 블랙리스트에 등록한다. 만료된 토큰은 갱신할 수 없다.

**Response 200**

```json
{ "token": "new-service-jwt" }
```

### 1-3. 로그아웃

`POST /auth/logout` · USER

현재 JWT를 서버 메모리 블랙리스트에 등록한다.

**Response 200, text/plain**

```text
로그아웃 되었습니다.
```

### 1-4. 레거시 카카오 경로

아래 API도 현재 코드에 남아 있으나 신규 연동은 `/auth/*`를 사용한다.

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| GET | `/api/kakao/callback?code={code}` | 공개 | 쿼리 파라미터 방식 카카오 로그인 |
| POST | `/api/kakao/logout` | USER | 레거시 로그아웃 |
| GET | `/api/kakao/me` | USER | JWT의 `email`, `role` 조회 |

---

## 2. 사업장 API

### 2-1. 사업장 목록 검색

`GET /workspaces` · 공개

승인 리뷰가 있어 `cleanScore`가 계산된 사업장만 점수 내림차순으로 반환한다.

**Query Parameters**

| 이름 | 필수 | 설명 |
|---|---|---|
| `status` | 아니오 | `EXCELLENT`, `NORMAL`, `CAUTION`, `DANGER` |
| `keyword` | 아니오 | 이름·주소·업종·상권 부분 검색 |

**Response 200**

```json
[
  {
    "workspaceId": 1,
    "name": "스타벅스 전남대점",
    "address": "광주광역시 북구 ...",
    "category": "카페",
    "district": "정문",
    "latitude": 35.1812,
    "longitude": 126.9088,
    "cleanScore": 88,
    "status": "EXCELLENT",
    "dominantReviewSentiment": "POSITIVE"
  }
]
```

감정 통계가 없거나 공동 1위면 `dominantReviewSentiment`는 `null`이다.

### 2-2. 자연어 사업장 검색

`GET /workspaces/nl-search` · 공개

Upstage Solar가 자연어를 검색 조건으로 변환하고, 실제 검색은 변환된 조건으로 DB에서 수행한다.

**Query Parameters**

| 이름 | 필수 | 설명 |
|---|---|---|
| `query` | 예 | 예: `상대에 클린점수 70점 넘는 카페` |

**Response 200**

```json
{
  "interpreted": {
    "minScore": 70,
    "maxScore": null,
    "district": "상대",
    "category": "카페",
    "keyword": null
  },
  "results": []
}
```

`results`의 각 항목은 2-1의 사업장 목록 응답과 같다.

**오류**

- `400`: 검색어가 비어 있음
- `503`: `UPSTAGE_API_KEY` 미설정
- `502`: Solar 호출 또는 응답 해석 실패

### 2-3. DB·카카오 통합 장소 검색

`GET /workspaces/place-search` · 공개

우리 DB 검색 결과와 카카오 로컬 키워드 검색 결과를 합쳐 반환한다. `kakaoPlaceId`를 우선 사용하고, 필요하면 이름과 주소로 중복을 제거한다.

**Query Parameters**

| 이름 | 필수 | 설명 |
|---|---|---|
| `keyword` | 예 | 장소 검색어 |

**Response 200**

```json
[
  {
    "existing": true,
    "workspaceId": 1,
    "kakaoPlaceId": "26338954",
    "name": "기존 사업장",
    "address": "광주광역시 북구 ...",
    "category": "카페",
    "latitude": 35.1812,
    "longitude": 126.9088,
    "cleanScore": 88,
    "status": "EXCELLENT"
  },
  {
    "existing": false,
    "workspaceId": null,
    "kakaoPlaceId": "18572431",
    "name": "신규 장소",
    "address": "광주광역시 북구 ...",
    "category": "음식점",
    "latitude": 35.1799,
    "longitude": 126.9075,
    "cleanScore": null,
    "status": null
  }
]
```

카카오 호출이 실패해도 DB 검색 결과가 있으면 DB 결과만 반환한다. DB 결과도 없으면 `502`를 반환한다.

### 2-4. 카카오 장소를 사업장으로 변환

`POST /workspaces/resolve` · USER

`kakaoPlaceId`가 이미 등록돼 있으면 기존 사업장을 재사용하고, 없으면 신규 사업장을 생성한다.

**Request**

```json
{
  "kakaoPlaceId": "18572431",
  "name": "신규 장소",
  "address": "광주광역시 북구 ...",
  "category": "카페",
  "latitude": 35.1799,
  "longitude": 126.9075
}
```

**Response 200**

```json
{ "workspaceId": 31, "created": true }
```

동시에 같은 장소를 등록해도 `kakaoPlaceId` 유니크 제약을 이용해 하나를 재사용한다.

### 2-5. 사업장 요약

`GET /workspaces/{workspaceId}/summary` · 공개

**Response 200**

```json
{
  "workspaceId": 1,
  "name": "스타벅스 전남대점",
  "address": "광주광역시 북구 ...",
  "category": "카페",
  "district": "정문",
  "latitude": 35.1812,
  "longitude": 126.9088,
  "cleanScore": 88,
  "status": "EXCELLENT",
  "reviewCount": 4,
  "checklistStats": [
    { "item": "CONTRACT", "compliantCount": 3, "violationCount": 1 }
  ],
  "reviewSummary": "승인된 후기 중 첫 번째 비어 있지 않은 내용",
  "reviewSentimentStats": {
    "positiveCount": 2,
    "neutralCount": 1,
    "negativeCount": 1,
    "positiveRate": 50,
    "neutralRate": 25,
    "negativeRate": 25
  },
  "dominantReviewSentiment": "POSITIVE"
}
```

`checklistStats`에는 8개 체크리스트 항목이 모두 포함된다. 사업장이 없으면 `404`다.

### 2-6. 사업장 상세

`GET /workspaces/{workspaceId}` · 공개

2-5의 기본 정보·체크리스트·감정 통계와 함께 동시 근무자 통계 및 승인된 공개 후기 목록을 반환한다. 이 응답에는 `reviewSummary`가 없다.

**추가 응답 필드**

```json
{
  "simultaneousWorkerStats": [
    {
      "dayType": "weekday",
      "timeSlot": "morning",
      "averageCoworkerCount": 2.5,
      "reviewCount": 2
    }
  ],
  "reviews": [
    {
      "reviewId": 10,
      "contractViolation": false,
      "minimumWageViolation": true,
      "weeklyAllowanceViolation": false,
      "breakTimeViolation": false,
      "wageDelayViolation": false,
      "scheduleChangeViolation": false,
      "substituteCoercionViolation": false,
      "overtimePayViolation": false,
      "coworkerCount": 2,
      "dayType": "weekday",
      "timeSlot": "morning",
      "content": "후기 내용",
      "createdAt": "2026-07-21T01:00:00"
    }
  ]
}
```

공개 후기에는 작성자 식별 정보가 포함되지 않는다.

### 2-7. 사업장 수동 등록

`POST /workspaces` · ADMIN

**Request**

```json
{
  "name": "사업장명",
  "address": "주소",
  "category": "카페",
  "district": "정문",
  "latitude": 35.1812,
  "longitude": 126.9088
}
```

**Response 201**

신규 사업장의 `WorkspaceSummaryResponse`를 반환한다. 승인 리뷰가 없으므로 `cleanScore`, `status`, `reviewSummary`, `dominantReviewSentiment`는 `null`이고 통계는 0이다.

### 2-8. 후기 작성

`POST /workspaces/{workspaceId}/reviews` · USER

**Request**

```json
{
  "contractViolation": false,
  "minimumWageViolation": true,
  "weeklyHolidayAllowanceViolation": false,
  "breakTimeViolation": false,
  "wageDelayViolation": false,
  "scheduleChangeViolation": false,
  "substituteDemandViolation": false,
  "overtimePayViolation": false,
  "coworkerCount": 2,
  "content": "후기 내용",
  "dayType": "weekday",
  "timeSlot": "morning"
}
```

체크리스트 8개, `coworkerCount`, `dayType`, `timeSlot`은 필수다. `coworkerCount`는 0 이상이어야 하며 `content`는 선택이다.

**Response 201**

```json
{
  "reviewId": 10,
  "workspaceId": 1,
  "status": "PENDING",
  "content": "후기 내용",
  "coworkerCount": 2,
  "dayType": "weekday",
  "timeSlot": "morning",
  "violationItems": ["MINIMUM_WAGE"],
  "createdAt": "2026-07-21T01:00:00"
}
```

작성자 키는 이메일이 아니라 JWT의 카카오 ID를 이용한 `kakao:{id}` 형식으로 저장한다.

### 2-9. 클린지수 수동 재계산

`POST /workspaces/{workspaceId}/clean-score/recalculate` · ADMIN

승인된 리뷰만 사용해 클린지수를 다시 계산하고 2-5 형식의 사업장 요약을 반환한다.

---

## 3. 리뷰 API

### 3-1. AI 후기 순화 미리보기

`POST /reviews/purity-preview` · USER

Upstage Solar Pro 2 `solar-pro2` 모델로 법적 위험도를 평가하고 뉘앙스가 다른 순화 후보 3개를 반환한다. 결과를 자동 저장하지 않는다.

**Request**

```json
{ "reviewText": "순화할 후기 원문" }
```

**Response 200**

```json
{
  "original_text": "순화할 후기 원문",
  "risk_assessment": {
    "risk_level": "HIGH",
    "detected_issues": ["탐지된 문제"],
    "reasoning": "순화가 필요한 이유"
  },
  "purified_options": [
    { "option_id": 1, "style": "매우 건조하고 객관적인 사실 전달형", "text": "순화안 1" },
    { "option_id": 2, "style": "부드럽고 완곡한 경험 공유형", "text": "순화안 2" },
    { "option_id": 3, "style": "원문의 감정을 유지하되 법적 문제만 제거", "text": "순화안 3" }
  ]
}
```

**오류**

- `400`: `reviewText`가 비어 있음
- `503`: `UPSTAGE_API_KEY` 미설정
- `502`: Solar 호출 또는 응답 해석 실패

### 3-2. 인증자료 첨부

`POST /reviews/{reviewId}/attachments` · USER

본인 리뷰 또는 ADMIN만 첨부할 수 있다.

**Content-Type**

```http
multipart/form-data
```

**Request Part**

- `file`: 업로드 파일

**제한**

- 확장자: `jpg`, `jpeg`, `png`, `pdf`
- MIME: `image/jpeg`, `image/png`, `application/pdf`
- 파일 시그니처 검사
- 파일당 최대 10MB
- 리뷰당 최대 5개
- 사용자당 최대 20개, 총 50MB

**Response 201**

```json
{
  "attachmentId": 5,
  "reviewId": 10,
  "fileName": "근로계약서.pdf",
  "contentType": "application/pdf",
  "size": 204800
}
```

운영 환경에서는 파일을 S3에 저장하고 DB에는 파일 메타데이터와 S3 키를 저장한다.

---

## 4. 사용자 API

### 4-1. 내 후기 목록

`GET /users/me/reviews` · USER

현재 사용자의 후기 전체를 상태와 관계없이 최신순으로 반환한다.

**Response 200**

```json
[
  {
    "reviewId": 10,
    "workspaceId": 1,
    "workspaceName": "사업장명",
    "contractViolation": false,
    "minimumWageViolation": true,
    "weeklyAllowanceViolation": false,
    "breakTimeViolation": false,
    "wageDelayViolation": false,
    "scheduleChangeViolation": false,
    "substituteCoercionViolation": false,
    "overtimePayViolation": false,
    "coworkerCount": 2,
    "content": "후기 내용",
    "status": "PENDING",
    "createdAt": "2026-07-21T01:00:00"
  }
]
```

---

## 5. 관리자 API

모든 `/admin/*` API는 ADMIN JWT가 필요하다.

### 5-1. 상태별 리뷰 목록

`GET /admin/reviews`

**Query Parameters**

| 이름 | 기본값 | 제약 |
|---|---|---|
| `status` | `PENDING` | `PENDING`, `APPROVED`, `REJECTED`, 대소문자 무관 |
| `page` | `0` | 0 이상 |
| `size` | `20` | 1~50 |

**Response 200**

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

`content[]`은 5-2의 `AdminReviewResponse`와 같은 필드를 가지지만 `attachments`는 빈 배열이고 `attachmentCount`만 제공한다.

### 5-2. 관리자 리뷰 상세

`GET /admin/reviews/{reviewId}`

**Response 200: `AdminReviewResponse`**

```json
{
  "reviewId": 10,
  "workspaceId": 1,
  "workspaceName": "사업장명",
  "category": "카페",
  "district": "정문",
  "address": "광주광역시 북구 ...",
  "authorEmail": "kakao:123456789",
  "contractViolation": false,
  "minimumWageViolation": true,
  "weeklyAllowanceViolation": false,
  "breakTimeViolation": false,
  "wageDelayViolation": false,
  "scheduleChangeViolation": false,
  "substituteCoercionViolation": false,
  "overtimePayViolation": false,
  "coworkerCount": 2,
  "content": "후기 내용",
  "sentiment": "NEGATIVE",
  "status": "PENDING",
  "createdAt": "2026-07-21T01:00:00Z",
  "updatedAt": "2026-07-21T01:00:00Z",
  "attachmentCount": 1,
  "attachments": [
    {
      "attachmentId": 5,
      "fileName": "근로계약서.pdf",
      "contentType": "application/pdf",
      "size": 204800
    }
  ]
}
```

필드명은 레거시 호환 때문에 `authorEmail`이지만 신규 리뷰에서는 `kakao:{id}` 작성자 키가 반환된다.

### 5-3. 인증자료 다운로드

`GET /admin/reviews/{reviewId}/attachments/{attachmentId}`

**Response 200**

- `Content-Type`: 저장된 파일 MIME, 파싱할 수 없으면 `application/octet-stream`
- `Content-Disposition`: `attachment; filename=...`
- `Content-Length`: 저장된 파일 크기
- Body: 파일 스트림

리뷰와 첨부파일 조합이 없거나 저장 객체가 없으면 `404`다.

### 5-4. 리뷰 승인·반려

`PATCH /admin/reviews/{reviewId}/status`

**Request**

```json
{ "status": "APPROVED" }
```

`APPROVED` 또는 `REJECTED`만 허용한다. 이미 검수된 리뷰는 다시 변경할 수 없다.

**Response 200**

```json
{
  "reviewId": 10,
  "status": "APPROVED",
  "cleanScore": 88,
  "workspaceStatus": "EXCELLENT"
}
```

승인 시 해당 사업장의 승인 리뷰 평균으로 클린지수를 갱신한다. 반려 시 기존 클린지수는 변경하지 않는다.

**오류**

- `400`: `PENDING`, `null` 등 허용되지 않는 상태
- `404`: 리뷰 또는 사업장 없음
- `409`: 이미 검수된 리뷰

### 5-5. 주관식 후기 내용 수정

`PATCH /admin/reviews/{reviewId}/content`

**Request**

```json
{ "content": "수정된 후기 내용" }
```

앞뒤 공백을 제거하며 빈 문자열은 `null`로 저장한다. `content` 자체가 `null`이면 `400`이다.

**Response 200**

```json
{
  "reviewId": 10,
  "content": "수정된 후기 내용",
  "updatedAt": "2026-07-21T02:00:00"
}
```

### 5-6. 후기 감정 수정

`PATCH /admin/reviews/{reviewId}/sentiment`

**Request**

```json
{ "sentiment": "POSITIVE" }
```

`POSITIVE`, `NEUTRAL`, `NEGATIVE` 중 하나가 필수다.

**Response 200**

```json
{
  "reviewId": 10,
  "sentiment": "POSITIVE",
  "updatedAt": "2026-07-21T02:00:00"
}
```

### 5-7. 관리자 통계

`GET /admin/stats`

**Response 200**

```json
{
  "totalReviews": 37,
  "pendingReviews": 5,
  "approvedReviews": 28,
  "rejectedReviews": 4,
  "totalWorkspaces": 42
}
```

---

## 6. 전체 엔드포인트 요약

### 권장 경로 22개

| Method | Path | 인증 |
|---|---|---|
| POST | `/auth/kakao/callback` | 공개 |
| POST | `/auth/refresh` | USER |
| POST | `/auth/logout` | USER |
| GET | `/workspaces` | 공개 |
| GET | `/workspaces/nl-search` | 공개 |
| GET | `/workspaces/place-search` | 공개 |
| POST | `/workspaces/resolve` | USER |
| GET | `/workspaces/{workspaceId}/summary` | 공개 |
| GET | `/workspaces/{workspaceId}` | 공개 |
| POST | `/workspaces` | ADMIN |
| POST | `/workspaces/{workspaceId}/reviews` | USER |
| POST | `/workspaces/{workspaceId}/clean-score/recalculate` | ADMIN |
| POST | `/reviews/purity-preview` | USER |
| POST | `/reviews/{reviewId}/attachments` | USER |
| GET | `/users/me/reviews` | USER |
| GET | `/admin/reviews` | ADMIN |
| GET | `/admin/reviews/{reviewId}` | ADMIN |
| GET | `/admin/reviews/{reviewId}/attachments/{attachmentId}` | ADMIN |
| PATCH | `/admin/reviews/{reviewId}/status` | ADMIN |
| PATCH | `/admin/reviews/{reviewId}/content` | ADMIN |
| PATCH | `/admin/reviews/{reviewId}/sentiment` | ADMIN |
| GET | `/admin/stats` | ADMIN |

### 레거시 경로 3개

| Method | Path | 인증 |
|---|---|---|
| GET | `/api/kakao/callback` | 공개 |
| POST | `/api/kakao/logout` | USER |
| GET | `/api/kakao/me` | USER |
