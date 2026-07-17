# 클린알바맵 백엔드 코드 흐름

이 문서는 코드를 처음 리뷰하는 사람이 "요청이 어디로 들어와 어떤 객체를 거쳐 응답이 되는지"를 빠르게 찾기 위한 안내서다.

## 1. 계층별 책임

```text
HTTP 요청
  -> controller: URL, 헤더, 요청 본문을 받고 HTTP 응답을 결정
  -> service: 조회, 계산, 외부 API 호출 등 유스케이스를 수행
  -> repository: JPA를 통해 데이터베이스를 조회하거나 저장
  -> domain: 데이터베이스 엔티티와 클린지수 계산 규칙을 보유
  -> dto: 외부 JSON과 내부 도메인 객체 사이의 입력·출력 형태를 정의
```

- `config`: 애플리케이션 시작 작업, CORS, JWT 필터 등록을 담당한다.
- `util`: JWT 발급·해석과 로그아웃 토큰 차단을 담당한다.
- `resources/application.yml`: 외부 서비스와 JWT 설정을 제공한다.
- `resources/data.sql`: 로컬 H2 실행 시 사업장과 승인/대기 리뷰의 예시 데이터를 넣는다.

## 2. 애플리케이션 시작 흐름

```text
CleanAlbaBackendApplication.main
  -> Spring 컨테이너와 웹 서버 시작
  -> JPA가 엔티티 기준으로 테이블 준비
  -> data.sql이 로컬 시드 데이터 삽입
  -> CleanScoreInitializer.run
  -> WorkspaceService.recalculateAllCleanScores
  -> 각 사업장의 승인된 리뷰 평균을 Workspace.cleanScore에 반영
```

클린지수는 리뷰의 원본 값이 아니라 승인된 리뷰에서 계산되는 파생 값이다. 리뷰 한 건은 100점에서 8개 위반 항목마다 12.5점을 감점하고, 사업장 점수는 승인 리뷰 점수의 평균이다. 승인 리뷰가 없으면 `cleanScore`는 `null`이며 목록 검색에서 제외된다.

## 3. 사업장 API 흐름

### 목록 조회: `GET /workspaces`

```text
WorkspaceController.getWorkspaceList
  -> WorkspaceService.getWorkspaceList
  -> 상태를 점수 범위로, 빈 검색어를 null로 정규화
  -> WorkspaceRepository.search
  -> WorkspaceListResponse.from
  -> 점수를 반올림하고 WorkspaceStatus를 계산해 JSON 응답
```

### 상세 요약: `GET /workspaces/{workspaceId}/summary`

`WorkspaceService`가 ID로 사업장을 찾고, 승인된 후기 수와 8개 체크 항목의 준수/위반 통계를 함께 `WorkspaceSummaryResponse`로 변환한다. `GET /workspaces/{workspaceId}`는 같은 요약에 승인 후기 목록까지 포함한다.

### 사업장 등록: `POST /workspaces`

컨트롤러가 Bearer JWT의 유효성과 `ADMIN` 역할을 확인한다. 서비스는 필수 입력을 검사하고 공백을 정리한 뒤 사업장을 저장한다. 새 사업장은 아직 리뷰가 없으므로 클린지수가 `null`이다.

### 리뷰 작성과 첨부

`POST /workspaces/{workspaceId}/reviews`는 JWT subject를 작성자로 사용하고 서버가 상태를 `PENDING`으로 고정한다. `POST /reviews/{reviewId}/attachments`는 작성자 또는 관리자만 호출할 수 있으며, 리뷰당 최대 5개의 10MB 이하 jpg/jpeg/png/pdf 파일을 확장자·MIME·파일 시그니처까지 확인한 뒤 저장한다. `GET /users/me/reviews`는 현재 JWT 작성자의 리뷰만 최신순으로 반환한다.

## 4. 카카오 로그인과 JWT 흐름

```text
프론트엔드가 카카오에서 인가 코드 획득
  -> POST /auth/kakao/callback { "code": "..." }
  -> KakaoService.getAccessToken: 인가 코드를 카카오 액세스 토큰으로 교환
  -> KakaoService.getUserInfo: 이메일·닉네임·카카오 ID 조회
  -> JwtUtil.generateToken: 이메일, 카카오 ID, 현재 역할, 고유 jti를 담은 서비스 JWT 발급
  -> KakaoLoginResponse 반환
```

- 기존 `GET /api/kakao/callback` 경로도 호환을 위해 유지한다.
- `GET /api/kakao/me`: JWT에서 이메일과 역할을 읽어 반환한다.
- `POST /api/kakao/logout`, `POST /auth/logout`: JWT를 메모리 블랙리스트에 넣는다.
- `POST /auth/refresh`: 현재 관리자 ID 설정으로 역할을 다시 계산한 새 `jti` 토큰을 만들고 이전 토큰을 폐기한다.
- `JwtAuthFilter`: 모든 요청을 관찰하고 보호 경로의 인증·관리자 권한을 요청 본문 파싱 전에 검사한다.

블랙리스트는 현재 프로세스 메모리에만 있으므로 서버 재시작 시 사라지고, 여러 서버 인스턴스 사이에 공유되지 않는다. 배포 전에 발급되어 `kakaoId` claim이 없는 JWT는 갱신할 수 없으므로 사용자가 한 번 다시 로그인해야 한다.

## 5. 관리자 검수 흐름

```text
GET /admin/reviews?status=pending
  -> 최신순 검수 목록 조회
GET /admin/reviews/{reviewId}
  -> 작성자와 체크리스트를 포함한 상세 조회
PATCH /admin/reviews/{reviewId}/status
  -> 리뷰와 사업장을 순서대로 잠금
  -> PENDING 상태만 APPROVED 또는 REJECTED로 1회 변경
  -> APPROVED일 때만 승인 후기 평균으로 클린지수 갱신
```

`GET /admin/stats`는 전체·대기·승인·반려 후기 수와 사업장 수를 반환한다. `POST /workspaces/{workspaceId}/clean-score/recalculate`는 관리자만 수동 정합성 보정을 실행할 수 있다.

## 6. 후기 순화 흐름

```text
POST /reviews/purity-preview
  -> ReviewController.purifyPreview
  -> PurifyService.purify
  -> 입력과 UPSTAGE_API_KEY 확인
  -> Solar API에 시스템 프롬프트와 후기 원문 전송
  -> choices[0].message.content의 JSON 텍스트 파싱
  -> 위험도와 순화 문장 3개를 JSON으로 반환
```

Solar가 JSON을 마크다운 코드 펜스로 감싸는 경우 `stripCodeFence`가 펜스를 제거한다. 외부 API 호출 실패나 응답 파싱 실패는 502로 변환된다.

## 7. 리뷰할 때 주의해서 볼 경계

- JWT 필터는 보호 API의 인증을 본문·multipart 파싱보다 먼저 수행하고, 컨트롤러도 유스케이스 직전에 권한을 다시 확인한다.
- `WorkspaceListResponse`와 `WorkspaceSummaryResponse`의 변환 로직은 동일하지만 서로 다른 API 계약을 독립적으로 표현한다.
- 클린지수 검색은 `null` 점수 사업장을 제외하며, 상한은 미포함이다.
- 후기 엔티티의 상태가 `APPROVED`인 경우만 클린지수 집계 대상이다.
- 반려는 기존 클린지수를 변경하지 않으며, 승인과 점수 갱신은 같은 트랜잭션에서 처리한다.
- 외부 API DTO의 `@JsonProperty`는 카카오의 snake_case 필드와 Java camelCase 필드를 연결한다.
