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

`WorkspaceService`가 ID로 사업장을 찾고, 없으면 404를 발생시킨다. 조회된 엔티티는 `WorkspaceSummaryResponse`로 변환된다.

### 사업장 등록: `POST /workspaces`

컨트롤러가 Bearer JWT의 유효성과 `ADMIN` 역할을 확인한다. 서비스는 필수 입력을 검사하고 공백을 정리한 뒤 사업장을 저장한다. 새 사업장은 아직 리뷰가 없으므로 클린지수가 `null`이다.

## 4. 카카오 로그인과 JWT 흐름

```text
프론트엔드가 카카오에서 인가 코드 획득
  -> GET /api/kakao/callback?code=...
  -> KakaoService.getAccessToken: 인가 코드를 카카오 액세스 토큰으로 교환
  -> KakaoService.getUserInfo: 이메일·닉네임·카카오 ID 조회
  -> JwtUtil.generateToken: 이메일(subject)과 역할(role)을 담은 서비스 JWT 발급
  -> KakaoLoginResponse 반환
```

- `GET /api/kakao/me`: JWT에서 이메일과 역할을 읽어 반환한다.
- `POST /api/kakao/logout`: JWT를 메모리 블랙리스트에 넣는다.
- `POST /auth/refresh`: 유효하고 블랙리스트에 없는 JWT의 이메일·역할을 유지해 새 JWT를 만든다.
- `JwtAuthFilter`: `/api/*` 요청의 토큰이 블랙리스트에 있으면 컨트롤러 전에 401로 차단한다.

블랙리스트는 현재 프로세스 메모리에만 있으므로 서버 재시작 시 사라지고, 여러 서버 인스턴스 사이에 공유되지 않는다.

## 5. 후기 순화 흐름

```text
POST /reviews/purify-preview
  -> ReviewController.purifyPreview
  -> PurifyService.purify
  -> 입력과 UPSTAGE_API_KEY 확인
  -> Solar API에 시스템 프롬프트와 후기 원문 전송
  -> choices[0].message.content의 JSON 텍스트 파싱
  -> 위험도와 순화 문장 3개를 JSON으로 반환
```

Solar가 JSON을 마크다운 코드 펜스로 감싸는 경우 `stripCodeFence`가 펜스를 제거한다. 외부 API 호출 실패나 응답 파싱 실패는 502로 변환된다.

## 6. 리뷰할 때 주의해서 볼 경계

- JWT 필터는 `/api/*`에만 등록되어 `/workspaces`와 `/auth`의 인증은 각 컨트롤러가 직접 처리한다.
- `WorkspaceListResponse`와 `WorkspaceSummaryResponse`의 변환 로직은 동일하지만 서로 다른 API 계약을 독립적으로 표현한다.
- 클린지수 검색은 `null` 점수 사업장을 제외하며, 상한은 미포함이다.
- 후기 엔티티의 상태가 `APPROVED`인 경우만 클린지수 집계 대상이다.
- 외부 API DTO의 `@JsonProperty`는 카카오의 snake_case 필드와 Java camelCase 필드를 연결한다.
