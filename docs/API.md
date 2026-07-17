# 클린알바맵 백엔드 API 명세서

프론트엔드 연동용 계약 문서. 현재 구현된 전체 엔드포인트 기준.

## 0. 공통 사항

### Base URL
- 로컬: `http://localhost:8080`
- 배포: `https://<EC2 도메인>` (HTTPS)

### 인증
- 로그인 후 발급받은 JWT를 **모든 보호 API 요청 헤더**에 담아 보낸다.
  ```
  Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5c...
  ```
- 아래 표에서 인증 표기:
  - 🔓 공개 (토큰 불필요)
  - 🔒 로그인 필요 (USER 이상)
  - 👑 관리자 전용 (ADMIN)

### 공통 에러 응답
- **컨트롤러/서비스 에러**(404, 400, 409 등): JSON
  ```json
  { "timestamp": "2026-07-12T07:25:01.646Z", "status": 404, "error": "Not Found", "path": "/workspaces/9999/summary" }
  ```
- **인증 필터 에러**(401/403): `text/plain` 본문 (한글 메시지)
  ```
  유효하지 않은 토큰입니다.
  ```

### 공통 enum
- **WorkspaceStatus** (클린 등급, `cleanScore`에서 파생)
  | 값 | 조건 | 색 |
  |---|---|---|
  | `EXCELLENT` | 80 이상 | 🟢 우수 |
  | `NORMAL` | 60~79 | 🟡 보통 |
  | `CAUTION` | 40~59 | 🟠 주의 |
  | `DANGER` | 40 미만 | 🔴 위험 |
  | `null` | 승인 리뷰 없음 | 지도·목록 미노출 |
- **ReviewStatus**: `PENDING`(검수 대기) / `APPROVED`(승인·집계 반영) / `REJECTED`(반려)

### 체크리스트 8항목 (객관식, boolean · true = 위반 경험 있음)
리뷰 1건 점수 = `100 - 12.5 × 위반 개수`. 사업장 클린지수 = 승인 리뷰 점수 평균.

| 요청 필드(boolean) | checklistStats item 코드 | 의미 |
|---|---|---|
| `contractViolation` | `CONTRACT` | 근로계약서 미작성 |
| `minimumWageViolation` | `MINIMUM_WAGE` | 최저임금 미준수 |
| `weeklyAllowanceViolation` | `WEEKLY_ALLOWANCE` | 주휴수당 미지급 |
| `breakTimeViolation` | `BREAK_TIME` | 휴게시간 부족 |
| `wageDelayViolation` | `WAGE_DELAY` | 급여 지급 지연 |
| `scheduleChangeViolation` | `SCHEDULE_CHANGE` | 사전 협의 없는 스케줄 변경 |
| `substituteCoercionViolation` | `SUBSTITUTE_COERCION` | 반복적 대타 강요 |
| `overtimePayViolation` | `OVERTIME_PAY` | 초과근무 수당 미지급 |

---

## 1. 인증 (Auth)

### 1-1. 🔓 카카오 로그인 (JWT 발급)
`POST /auth/kakao/callback`

프론트가 카카오에서 받은 인가 코드를 넘기면, 백엔드가 카카오와 통신 후 우리 서비스 JWT를 발급한다.

**Request Body**
| Key | Type | 설명 |
|---|---|---|
| code | String | 카카오 인가 코드 |

**Response Body** (`KakaoLoginResponse`)
| Key | Type | 설명 |
|---|---|---|
| token | String | 우리 서비스 JWT (이후 요청에 사용) |
| userEmail | String | 카카오 이메일 |
| nickname | String | 카카오 닉네임 |
| role | String | `USER` 또는 `ADMIN` |

```json
// 요청
{ "code": "abcd1234..." }
// 응답
{ "token": "eyJhbGciOi...", "userEmail": "user@kakao.com", "nickname": "다은", "role": "USER" }
```

### 1-2. 🔒 토큰 갱신
`POST /auth/refresh` · 헤더 `Authorization` 필요

아직 유효한 토큰을 보내면 만료시간을 새로 찍은 토큰을 재발급(슬라이딩 갱신). **만료 후에는 갱신 불가** → 만료 전에 호출.

**Response Body**
```json
{ "token": "eyJhbGciOi...(새 토큰)" }
```

### 1-3. 🔒 로그아웃
`POST /auth/logout` · 헤더 `Authorization` 필요

해당 토큰을 블랙리스트에 등록(이후 사용 불가).
**Response Body**: `"로그아웃 되었습니다."` (text)

> ⚠️ 참고: 구 엔드포인트 `GET /api/kakao/callback`, `POST /api/kakao/logout`, `GET /api/kakao/me`가 아직 남아있으나 **`/auth/*`와 중복**이다. FE는 `/auth/*`를 사용하고, 백엔드는 추후 `/api/kakao/*` 제거 권장.

---

## 2. 사업장 (Workspaces)

### 2-1. 🔓 지도 핀/리스트 조회 (클린지수 정렬 + 검색)
`GET /workspaces`

승인 리뷰가 있어 클린지수가 산출된 사업장만, 클린지수 내림차순으로 반환.

> `status` 필터는 **응답 `cleanScore`(반올림)의 등급과 정확히 일치**한다. 예: 저장 점수 79.5는 반올림 80 → `EXCELLENT`로 노출되고 `status=EXCELLENT`에서만 조회된다(`status=NORMAL`에는 안 나옴).

**Query Parameter**
| Key | Type | 설명 |CLAUD
|---|---|---|
| status | String (선택) | 등급 필터. `EXCELLENT`/`NORMAL`/`CAUTION`/`DANGER` |
| keyword | String (선택) | 이름·주소·업종·상권 부분검색 |

**Response Body** (배열, `WorkspaceListResponse`)
| Key | Type | 설명 |
|---|---|---|
| workspaceId | Long | 사업장 ID |
| name | String | 이름 |
| address | String | 주소 |
| category | String | 업종 |
| district | String\|null | 상권(상대/예대 등). 카카오 신규 장소는 null |
| latitude / longitude | Number | 좌표 |
| cleanScore | Integer | 클린지수(반올림) |
| status | String | 등급(WorkspaceStatus) |

```json
[
  { "workspaceId":1, "name":"스타벅스 전남대후문점", "address":"광주 북구 용봉로 105",
    "category":"카페", "district":"후문", "latitude":35.1812, "longitude":126.9088,
    "cleanScore":94, "status":"EXCELLENT" }
]
```

### 2-2. 🔓 통합 장소 검색 (후기 장소 선택 페이지)
`GET /workspaces/place-search`

기존 사업장 + 카카오 신규 장소를 **한 번에** 검색해 합쳐서 반환한다. 결과 구성:
1. **우리 DB 키워드 검색** — 이름·주소·업종·상권을 부분검색한 기존 사업장 전부 (`existing:true`). 이름 오름차순.
2. **카카오 로컬 검색** — 각 카카오 장소를 `kakaoPlaceId`(없으면 이름+주소)로 우리 DB와 대조. 이미 위 1번에 포함된 사업장이면 **중복 제외**, DB에 없으면 신규(`existing:false`)로 뒤에 추가.

> 이전과 달리 **카카오가 반환하지 않는 기존 사업장도 키워드로 잡히면 노출**되며, 같은 장소가 두 번 나오지 않도록 `workspaceId`·(이름+주소) 기준으로 중복 제거된다.

**Query Parameter**
| Key | Type | 설명 |
|---|---|---|
| keyword | String (필수) | 검색어 |

**Response Body** (배열, `WorkspacePlaceSearchResponse`)
| Key | Type | 설명 |
|---|---|---|
| existing | Boolean | 이미 등록된 사업장인지 |
| workspaceId | Long\|null | 기존이면 값, 신규면 null |
| kakaoPlaceId | String | 카카오 장소 ID |
| name / address / category | String | 장소 정보 |
| latitude / longitude | Number | 좌표 |
| cleanScore | Integer\|null | 기존이면 값, 신규면 null |
| status | String\|null | 기존이면 등급, 신규면 null |

> `category`는 비어있지 않다. 카카오 대분류(카페/음식점/편의점 등)가 없는 장소는 `기타`로 내려간다.

```json
[
  { "existing":true, "workspaceId":1, "kakaoPlaceId":"26338954", "name":"스타벅스 전남대후문점",
    "address":"광주 북구 용봉로 105", "category":"카페", "latitude":35.1812, "longitude":126.9088,
    "cleanScore":94, "status":"EXCELLENT" },
  { "existing":false, "workspaceId":null, "kakaoPlaceId":"18572431", "name":"이디야커피 용봉점",
    "address":"광주 북구 용봉로 90", "category":"카페", "latitude":35.1799, "longitude":126.9075,
    "cleanScore":null, "status":null }
]
```
> FE 흐름: `existing:true` → 그 `workspaceId`로 바로 `/review/write/{id}`. `existing:false` → 아래 2-3(resolve)로 workspaceId 확보 후 이동.

### 2-3. 🔒 카카오 장소 → workspaceId 변환(resolve)
`POST /workspaces/resolve`

카카오 신규 장소를 사업장으로 등록(중복 시 재사용)하고 workspaceId를 돌려준다. 후기 작성 진입 전에 호출.

**Request Body** (`WorkspaceResolveRequest`)
| Key | Type | 설명 |
|---|---|---|
| kakaoPlaceId | String (필수) | 중복확인 기준 |
| name / address / category | String | 장소 정보 |
| latitude / longitude | Number | 좌표 |

**Response Body** (`WorkspaceResolveResponse`)
| Key | Type | 설명 |
|---|---|---|
| workspaceId | Long | 생성 또는 재사용된 사업장 ID |
| created | Boolean | 신규 생성이면 true, 기존 재사용이면 false |

```json
// 요청
{ "kakaoPlaceId":"18572431", "name":"이디야커피 용봉점", "address":"광주 북구 용봉로 90",
  "category":"카페", "latitude":35.1799, "longitude":126.9075 }
// 응답
{ "workspaceId":31, "created":true }
```

### 2-4. 🔓 사업장 요약 (핀 클릭 팝업)
`GET /workspaces/{workspaceId}/summary`

**Response Body** (`WorkspaceSummaryResponse`): 2-1 필드 + 아래
| Key | Type | 설명 |
|---|---|---|
| reviewCount | Number | 승인 리뷰 수 |
| checklistStats | Array | 항목별 통계 (아래) |
| reviewSummary | String \| null | 승인 리뷰 중 내용이 있는 첫 후기 본문. 없으면 null |

`checklistStats[]` 원소: `{ "item":"CONTRACT", "compliantCount":3, "violationCount":1 }` (item 코드는 §0 표 참고)

### 2-5. 🔓 사업장 상세 (상세 페이지)
`GET /workspaces/{workspaceId}`

**Response Body** (`WorkspaceDetailResponse`): 요약(2-4) 필드 + `reviews` 배열(공개용 승인 리뷰 목록).
`reviews[]` 원소(`PublicReviewResponse`): reviewId, 체크리스트 8개(boolean), coworkerCount, content, createdAt. **작성자 이메일은 미포함**(공개용).

### 2-6. 👑 신규 사업장 등록 (관리자 수동)
`POST /workspaces`

**Request Body** (`WorkspaceCreateRequest`): name, address, category, district, latitude, longitude (모두 필수)
**Response Body**: `WorkspaceSummaryResponse` (201 Created). cleanScore는 리뷰 없어 null.

### 2-7. 🔒 후기 작성
`POST /workspaces/{workspaceId}/reviews`

**Request Body** (`ReviewCreateRequest`)
| Key | Type | 설명 |
|---|---|---|
| contractViolation ~ overtimePayViolation | Boolean (8개, 필수) | 체크리스트(§0) |
| coworkerCount | Integer (선택) | 동시간대 근무자 수(0 이상) |
| content | String (선택) | 주관식 후기 |

**Response Body** (`ReviewCreateResponse`, 201)
```json
{ "reviewId":10, "workspaceId":1, "status":"PENDING", "createdAt":"2026-07-12T16:00:00" }
```
> 작성 직후 상태는 `PENDING`. 관리자 승인 후에야 공개·집계된다.

### 2-8. 👑 클린점수 재계산
`POST /workspaces/{workspaceId}/clean-score/recalculate` → `WorkspaceSummaryResponse`

### 2-9. 🔓 자연어 검색
`GET /workspaces/nl-search`

"클린점수 60점 넘는 상대 카페 찾아줘" 같은 문장을 Solar LLM이 검색 조건으로 해석한 뒤, **검색 자체는 DB가** 수행한다(LLM이 사업장을 지어내지 않는다). 결과는 2-1과 동일하게 클린지수 내림차순.

**Query Parameter**
| Key | Type | 설명 |
|---|---|---|
| query | String (필수) | 자연어 검색어 |

**Response Body** (`WorkspaceNlSearchResponse`)
| Key | Type | 설명 |
|---|---|---|
| interpreted | Object | LLM이 해석한 검색 조건. 검색어에 없는 항목은 null |
| interpreted.minScore / maxScore | Integer\|null | 클린지수 하한·상한. **응답에 노출되는 반올림 점수 기준이며 양끝 포함** |
| interpreted.district | String\|null | 상권 (`상대`/`예대`/`정문`/`후문`) |
| interpreted.category | String\|null | 업종 (`카페`/`식당`/`편의점`/`주점` 등) |
| interpreted.keyword | String\|null | 상호명·주소 조각 |
| results | Array | 2-1과 동일한 `WorkspaceListResponse` 배열 |

```json
// GET /workspaces/nl-search?query=클린점수 60점 넘는 상대 카페 찾아줘
{
  "interpreted": { "minScore":60, "maxScore":null, "district":"상대", "category":"카페", "keyword":null },
  "results": [
    { "workspaceId":2, "name":"메가MGC커피 상대점", "address":"광주 북구 용봉동 1097-3",
      "category":"카페", "district":"상대", "latitude":35.1768, "longitude":126.9043,
      "cleanScore":88, "status":"EXCELLENT" }
  ]
}
```
> `interpreted`를 검색 조건 칩("상대 · 카페 · 60점 이상")으로 노출하면 사용자가 오해석을 바로 알아챌 수 있다.
> 2-1과 마찬가지로 **승인 리뷰가 있어 클린지수가 산출된 사업장만** 검색된다(등록만 되고 리뷰 없는 사업장은 안 나옴).
> `UPSTAGE_API_KEY` 미설정 시 503, LLM 호출·해석 실패 시 502 (3-1 순화 API와 동일 규칙).

---

## 3. 리뷰 (Reviews)

### 3-1. 🔒 후기 순화 미리보기 (AI)
`POST /reviews/purity-preview`

주관식 원문을 Upstage Solar LLM으로 분석 → 리스크 평가 + 뉘앙스가 다른 3가지 순화 버전 반환.

**Request Body**: `{ "reviewText": "원문..." }`
**Response Body** (LLM 생성 JSON)
```json
{
  "original_text": "...",
  "risk_assessment": { "risk_level": "HIGH|MEDIUM|LOW|SAFE", "detected_issues": ["..."], "reasoning": "..." },
  "purified_options": [
    { "option_id":1, "style":"매우 건조하고 객관적인 사실 전달형", "text":"..." },
    { "option_id":2, "style":"부드럽고 완곡한 경험 공유형", "text":"..." },
    { "option_id":3, "style":"감정 유지하되 법적 문제만 제거", "text":"..." }
  ]
}
```
> `UPSTAGE_API_KEY` 미설정 시 503, LLM 호출 실패 시 502.

### 3-2. 🔒 인증 자료 업로드 (첨부파일)
`POST /reviews/{reviewId}/attachments` · `multipart/form-data`

본인 리뷰에만 첨부 가능(또는 관리자). jpg/jpeg/png/pdf, 개별 10MB·리뷰당 5개·사용자당 20개/50MB 제한. 매직바이트 검증.

**Request Part**: `file` (MultipartFile)
**Response Body** (`ReviewAttachmentResponse`, 201)
```json
{ "attachmentId":5, "reviewId":10, "fileName":"계약서.pdf", "contentType":"application/pdf", "size":204800 }
```

---

## 4. 사용자 (Users)

### 4-1. 🔒 내 리뷰 목록
`GET /users/me/reviews`

로그인한 사용자가 작성한 리뷰 전체(상태 무관, 최신순).
**Response Body** (배열, `ReviewResponse`): reviewId, workspaceId, workspaceName, 체크리스트 8개, coworkerCount, content, status, createdAt.

---

## 5. 관리자 (Admin) — 👑 전체 ADMIN 전용

### 5-1. 검수 대기/상태별 리뷰 목록 (페이지네이션)
`GET /admin/reviews`

**Query Parameter**
| Key | Type | 기본 | 설명 |
|---|---|---|---|
| status | String | PENDING | `PENDING`/`APPROVED`/`REJECTED` |
| page | int | 0 | 0-based |
| size | int | 20 | 1~50 |

**Response Body** (`PagedResponse<AdminReviewResponse>`)
```json
{
  "content": [
    { "reviewId":10, "workspaceId":1, "workspaceName":"스타벅스 전남대후문점", "authorEmail":"user@kakao.com",
      "contractViolation":false, "minimumWageViolation":true, "...":"(체크리스트 8개)",
      "coworkerCount":2, "content":"...", "status":"PENDING", "createdAt":"2026-07-12T16:00:00" }
  ],
  "page":0, "size":20, "totalElements":37, "totalPages":2
}
```
> 관리자 응답에는 일반 응답과 달리 `authorEmail`이 포함된다.

### 5-2. 리뷰 상세 검수
`GET /admin/reviews/{reviewId}` → `AdminReviewResponse` (5-1 content 원소와 동일 구조)

### 5-3. 승인/반려
`PATCH /admin/reviews/{reviewId}/status`

**Request Body**: `{ "status": "APPROVED" }` 또는 `{ "status": "REJECTED" }` (`PENDING` 불가)
**Response Body** (`ReviewStatusUpdateResponse`)
```json
{ "reviewId":10, "status":"APPROVED", "cleanScore":88, "workspaceStatus":"EXCELLENT" }
```
> 승인 시 해당 사업장 클린지수가 재계산되어 함께 반환된다. 이미 검수된 리뷰 재검수 시 409.

### 5-4. 관리자 통계
`GET /admin/stats` → (`AdminStatsResponse`)
```json
{ "totalReviews":37, "pendingReviews":5, "approvedReviews":28, "rejectedReviews":4, "totalWorkspaces":42 }
```

---

## 부록. 엔드포인트 한눈에 보기

| # | Method | Path | 인증 | 설명 |
|---|---|---|---|---|
| 1 | POST | /auth/kakao/callback | 🔓 | 카카오 로그인·JWT 발급 |
| 2 | POST | /auth/refresh | 🔒 | 토큰 갱신 |
| 3 | POST | /auth/logout | 🔒 | 로그아웃 |
| 4 | GET | /workspaces | 🔓 | 지도/리스트(정렬·검색) |
| 5 | GET | /workspaces/place-search | 🔓 | 통합 장소 검색 |
| 6 | POST | /workspaces/resolve | 🔒 | 카카오 장소→workspaceId |
| 7 | GET | /workspaces/{id}/summary | 🔓 | 요약 팝업 |
| 8 | GET | /workspaces/{id} | 🔓 | 상세 |
| 9 | POST | /workspaces | 👑 | 사업장 등록 |
| 10 | POST | /workspaces/{id}/reviews | 🔒 | 후기 작성 |
| 11 | POST | /workspaces/{id}/clean-score/recalculate | 👑 | 클린점수 재계산 |
| 11-1 | GET | /workspaces/nl-search | 🔓 | 자연어 검색 (AI 조건 해석) |
| 12 | POST | /reviews/purity-preview | 🔒 | AI 후기 순화 |
| 13 | POST | /reviews/{id}/attachments | 🔒 | 인증자료 업로드 |
| 14 | GET | /users/me/reviews | 🔒 | 내 리뷰 목록 |
| 15 | GET | /admin/reviews | 👑 | 검수 목록 |
| 16 | GET | /admin/reviews/{id} | 👑 | 검수 상세 |
| 17 | PATCH | /admin/reviews/{id}/status | 👑 | 승인/반려 |
| 18 | GET | /admin/stats | 👑 | 통계 |
