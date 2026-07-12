# review-moderation-core - Work Plan

## TL;DR (For humans)
**What you'll get:** 로그인 사용자가 검수 대기 리뷰를 제출하고, 관리자가 목록과 상세를 확인해 승인 또는 반려하면 승인 결과가 클린지수와 기존 지도 목록에 즉시 반영되는 백엔드 수직 파이프라인입니다.

**Why this approach:** 기존 리뷰 점수식과 사업장 집계 로직을 재사용하면서, 검수 상태 변경과 점수 갱신을 하나의 트랜잭션으로 묶어 중복 승인과 부분 반영을 막습니다.

**What it will NOT do:** 첨부파일/S3, 관리자 통계, 내 리뷰 목록, 사업장 상세 확장, 카카오 경로 변경, 추천·계약서 분석은 이번 범위에 포함하지 않습니다.

**Effort:** Medium
**Risk:** Medium - 인증 필터 범위와 동시 관리자 검수의 트랜잭션 정합성이 핵심 위험입니다.
**Decisions to sanity-check:** 리뷰 작성 경로는 사업장 하위 경로 하나만 지원하고, 상태 전이는 검수 대기에서 승인/반려로 한 번만 허용하며, 목록은 최신순 페이지네이션을 사용합니다.

Your next move: 구현 시작 또는 고정밀 계획 검토 선택. Full execution detail follows below.

---

> TL;DR (machine): Medium effort/risk; protected review submission, paged admin moderation, atomic score update, HTTP verification.

## Scope
### Must have

- Authenticated `POST /workspaces/{workspaceId}/reviews` creates a review with server-owned `PENDING`, JWT subject author, and server timestamp.
- ADMIN-only paged pending queue, review detail, and status PATCH endpoints with fixed DTO contracts.
- Strict Bearer parsing for the new protected routes: missing/malformed/expired/invalid/blacklisted/blank-subject tokens return 401; USER on admin routes returns 403.
- Only `PENDING -> APPROVED|REJECTED`; repeated or conflicting processing returns 409.
- Review row then workspace row pessimistic locking, status flush, and approved-only score recalculation in one transaction.
- First approval makes an unscored workspace visible through existing `GET /workspaces`; rejection does not.
- PATCH is allowed by the existing CORS configuration without changing origins or credential policy.
- All existing user documentation/comment changes remain intact.

### Must NOT have (guardrails, anti-slop, scope boundaries)

- No attachment entity, S3 integration, admin stats, my-review endpoint, workspace detail/statistics expansion, or public recalculation endpoint.
- No Kakao callback/logout path migration, recommender, contract analyzer, job-posting model, or search redesign.
- No Spring Security dependency, new user table, refresh-token redesign, broad authentication rewrite, or entity serialization.
- Do not overwrite or revert the dirty worktree's existing comments, package-info files, or `CODE_FLOW.md`.

## Verification strategy
> Zero human intervention - all verification is agent-executed.
- Test decision: TDD with JUnit 5, Spring Boot JPA/WebMVC tests, MockMvc/RANDOM_PORT HTTP integration; every production behavior starts with a failing test.
- Evidence: each todo writes the explicit `.omo/evidence/task-{todo}-review-moderation-core.txt` path listed below.

- Baseline and final command: `$env:JAVA_HOME='C:\Users\ohyou\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2'; .\gradlew.bat clean test`.
- Targeted commands are listed per todo; final packaging uses the same JDK with `.\gradlew.bat clean test bootJar`.

## Execution strategy
### Parallel execution waves
> Target 5-8 todos per wave. Fewer than 3 (except the final) means you under-split.

- Wave 1: Todo 1 contract/error fixtures, Todo 2 authentication/CORS, Todo 3 repository query/locking foundations.
- Wave 2: Todo 4 review submission and Todo 5 admin read APIs after their Wave 1 dependencies.
- Wave 3: Todo 6 atomic moderation/score reflection.
- Wave 4: Todo 7 full real-HTTP vertical QA and regression/package gate.

### Dependency matrix
| Todo | Depends on | Blocks | Can parallelize with |
| --- | --- | --- | --- |
| 1 | none | 4, 5, 6, 7 | 2, 3 |
| 2 | none | 4, 5, 6, 7 | 1, 3 |
| 3 | none | 4, 5, 6, 7 | 1, 2 |
| 4 | 1, 2, 3 | 6, 7 | 5 |
| 5 | 1, 2, 3 | 6, 7 | 4 |
| 6 | 1, 2, 3, 4, 5 | 7 | none |
| 7 | 1-6 | final verification | none |

## Todos
> Implementation + Test = ONE todo. Never separate.
<!-- APPEND TASK BATCHES BELOW THIS LINE WITH edit/apply_patch - never rewrite the headers above. -->
- [ ] 1. Lock request/response and error contracts with failing tests
  What to do / Must NOT do: Add `ReviewCreateRequest`, `ReviewCreateResponse`, `AdminReviewListResponse`, `AdminReviewDetailResponse`, `ReviewStatusUpdateRequest`, `ReviewStatusUpdateResponse`, and a stable `PagedResponse<T>` under `dto`. Eight checklist inputs are boxed `Boolean` and all required; `coworkerCount` is nullable/non-negative, `content` is nullable/trimmed, and client JSON cannot set author/status/time. Add one small shared HTTP error response/exception mapping only if needed to assert consistent statuses; do not refactor unrelated existing endpoints. Write failing serialization/validation/controller-contract tests first for 400/404/409 shapes.
  Parallelization: Wave 1 | Blocked by: none | Blocks: 4, 5, 6, 7
  References (executor has NO interview context - be exhaustive): `src/main/java/com/cleanmap/clean_alba_backend/dto/WorkspaceCreateRequest.java`; `src/main/java/com/cleanmap/clean_alba_backend/dto/WorkspaceListResponse.java`; `src/main/java/com/cleanmap/clean_alba_backend/domain/Review.java:18-68`; `src/main/java/com/cleanmap/clean_alba_backend/domain/ReviewStatus.java`; existing error patterns in `controller/WorkspaceController.java:32-47` and `service/WorkspaceService.java:41-67`.
  Acceptance criteria (agent-executable): targeted JUnit command exits 0 and asserts omitted checklist field and invalid status/body return 400; DTO JSON contains only the approved fields and no entity/lazy proxy data.
  QA scenarios (name the exact tool + invocation): run `$env:JAVA_HOME='C:\Users\ohyou\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2'; .\gradlew.bat test --tests '*ReviewContractTest'`; happy asserts complete request binds, failure asserts missing Boolean and negative coworker count reject; Evidence `.omo/evidence/task-1-review-moderation-core.txt`.
  Commit: N | grouped with completed vertical slice

- [ ] 2. Establish the exact authentication boundary and PATCH CORS support with failing MockMvc tests
  What to do / Must NOT do: Extend `JwtAuthFilter` and its registration only enough to protect `POST /workspaces/{numericId}/reviews` and all `/admin/**` requests. OPTIONS bypasses auth. Strictly require one `Bearer <token>`, reject malformed/missing/invalid/expired/blacklisted/blank-subject tokens with 401, and attach verified email/role as request attributes. ADMIN routes require role in the controller/helper and return 403 for USER. Preserve existing `/api/*` blacklist behavior and existing `POST /workspaces`; do not add Spring Security. Add only `PATCH` to existing CORS methods and verify preflight.
  Parallelization: Wave 1 | Blocked by: none | Blocks: 4, 5, 6, 7
  References (executor has NO interview context - be exhaustive): `src/main/java/com/cleanmap/clean_alba_backend/util/JwtAuthFilter.java:17-43`; `util/JwtUtil.java:47-103`; `util/JwtBlacklistUtill.java`; `config/SecurityConfig.java:11-22`; `config/CorsConfig.java:10-22`; manual role check at `controller/WorkspaceController.java:32-47`.
  Acceptance criteria (agent-executable): MockMvc tests prove public `GET /workspaces` still works, protected review/admin routes enforce 401/403, blacklisted token is rejected, verified email cannot be supplied by body, and OPTIONS for PATCH returns allowed method headers.
  QA scenarios (name the exact tool + invocation): run `.\gradlew.bat test --tests '*JwtProtectedRouteTest' --tests '*CorsConfigTest'` with the approved JDK env; happy uses valid USER/ADMIN JWT, failure covers missing/malformed/blacklisted USER-admin calls; Evidence `.omo/evidence/task-2-review-moderation-core.txt`.
  Commit: N | grouped with completed vertical slice

- [ ] 3. Add review creation, paged reads, and deterministic locking repository foundations with failing JPA tests
  What to do / Must NOT do: Add review indexes for `(status, created_at, review_id)` and `(workspace_id, status, created_at, review_id)` only where they support this slice. Add `Page<Review>` status query with workspace fetched without N+1 and order `createdAt DESC, reviewId DESC`. Add pessimistic-write review lookup and pessimistic-write workspace lookup; always lock review then workspace. Keep current approved-review query for recalculation. Do not add attachment/my-review/stats queries or a migration framework in this slice; note that production MySQL index rollout remains an explicit deployment concern.
  Parallelization: Wave 1 | Blocked by: none | Blocks: 4, 5, 6, 7
  References (executor has NO interview context - be exhaustive): `domain/Review.java:15-68`; `domain/Workspace.java:14-47`; `repository/ReviewRepository.java:10-14`; `repository/WorkspaceRepository.java:10-32`; `src/main/resources/data.sql` seed statuses and ID ordering.
  Acceptance criteria (agent-executable): `@DataJpaTest` proves page size/order/tie-break, workspace access does not create one query per result, lock lookups return/404 correctly, and existing approved lookup remains correct.
  QA scenarios (name the exact tool + invocation): run `.\gradlew.bat test --tests '*ReviewRepositoryTest'`; happy asserts newest deterministic page, failure asserts missing locked IDs and excludes nonmatching statuses; Evidence `.omo/evidence/task-3-review-moderation-core.txt`.
  Commit: N | grouped with completed vertical slice

- [ ] 4. Implement authenticated review submission as a PENDING-only domain operation
  What to do / Must NOT do: Write failing service and MockMvc tests, then add the minimal `Review` creation path and `ReviewService`. `POST /workspaces/{workspaceId}/reviews` takes the approved DTO, resolves workspace or 404, reads author only from verified request attribute/JWT subject, trims content, sets server time and PENDING regardless of client JSON, saves once, and returns 201. Do not recalculate score on submission and do not expose JPA entities.
  Parallelization: Wave 2 | Blocked by: 1, 2, 3 | Blocks: 6, 7 | Can parallelize with: 5
  References (executor has NO interview context - be exhaustive): `domain/Review.java`; `controller/ReviewController.java:14-25`; `service/WorkspaceService.java:41-67` for not-found/transaction style; `repository/WorkspaceRepository.java`; DTOs from Todo 1 and auth attributes from Todo 2.
  Acceptance criteria (agent-executable): valid USER and ADMIN requests return 201 with `reviewId/workspaceId/PENDING/createdAt`; persisted author equals JWT subject; nonexistent workspace is 404; invalid body is 400; no score changes and an unscored workspace stays absent from `GET /workspaces`.
  QA scenarios (name the exact tool + invocation): run `.\gradlew.bat test --tests '*ReviewServiceTest' --tests '*ReviewSubmissionControllerTest'`; happy creates PENDING review, failure covers 401, 400, 404 and spoofed author/status fields; Evidence `.omo/evidence/task-4-review-moderation-core.txt`.
  Commit: N | grouped with completed vertical slice

- [ ] 5. Implement ADMIN pending queue and review detail without N+1 or entity leakage
  What to do / Must NOT do: Write failing service/MockMvc tests, then add `AdminReviewService` read methods and `AdminReviewController`. `GET /admin/reviews` defaults missing/`pending`/`PENDING` to PENDING, rejects other unknown values with 400, clamps/validates page and size (0/20, maximum 50), and returns stable page metadata plus workspace summary. `GET /admin/reviews/{id}` returns workspace, author, all eight flags, coworker count, content, status, and createdAt. Both are ADMIN-only and DTO-only; do not add stats or attachments.
  Parallelization: Wave 2 | Blocked by: 1, 2, 3 | Blocks: 6, 7 | Can parallelize with: 4
  References (executor has NO interview context - be exhaustive): DTOs from Todo 1; queries from Todo 3; `domain/ReviewStatus.java`; `domain/Review.java`; controller conventions in `WorkspaceController.java`.
  Acceptance criteria (agent-executable): ADMIN receives deterministic page/detail; missing review is 404; USER is 403; missing token is 401; lowercase/uppercase pending work; unknown status and oversized/negative page parameters are 400; query-count assertion or statistics shows bounded query count.
  QA scenarios (name the exact tool + invocation): run `.\gradlew.bat test --tests '*AdminReviewReadServiceTest' --tests '*AdminReviewControllerTest'`; happy lists/detail, failure covers auth/status/page/not-found; Evidence `.omo/evidence/task-5-review-moderation-core.txt`.
  Commit: N | grouped with completed vertical slice

- [ ] 6. Implement one-way moderation and atomic clean-score reflection under concurrent requests
  What to do / Must NOT do: Write failing domain, transaction, rollback, and conflict tests first. Add one-way transition behavior to `Review`; in one outer `@Transactional` admin service method lock review then workspace, reject non-PENDING with 409, apply APPROVED/REJECTED, flush review status, and on approval call/refactor the existing approved-only recalculation without opening a second independent transaction. Return review status and resulting rounded display score/status. Approval and score update must roll back together. Rejection must not create/change a score. Do not allow reopening or direct client score input.
  Parallelization: Wave 3 | Blocked by: 1, 2, 3, 4, 5 | Blocks: 7
  References (executor has NO interview context - be exhaustive): `domain/Review.java:62-78`; `domain/ReviewStatus.java`; `service/WorkspaceService.java:75-89`; locked repositories from Todo 3; `dto/WorkspaceListResponse.java:20-37` rounding/status behavior; `repository/WorkspaceRepository.java:17-28` visibility behavior.
  Acceptance criteria (agent-executable): approval of one-violation review stores raw 87.5 and existing workspace list shows 88; PENDING/REJECTED never enter average; first approval changes null to score and makes workspace visible; repeat/conflicting processing returns 409; concurrent same-review and same-workspace moderation cannot lose an approval; injected recalculation failure rolls back status and score.
  QA scenarios (name the exact tool + invocation): run `.\gradlew.bat test --tests '*ReviewModerationDomainTest' --tests '*ReviewModerationIntegrationTest'`; happy approval/rejection cases, failure repeat/concurrency/rollback cases; Evidence `.omo/evidence/task-6-review-moderation-core.txt`.
  Commit: N | grouped with completed vertical slice

- [ ] 7. Drive the complete pipeline through real HTTP and run the full regression/package gate
  What to do / Must NOT do: Add one RANDOM_PORT end-to-end integration scenario that creates a fresh unscored workspace fixture, proves it is absent, submits a USER review, verifies ADMIN queue/detail, verifies USER gets 403, approves as ADMIN, verifies repeat approval 409, and verifies `GET /workspaces` now includes the workspace with rounded score. Add a separate rejection path proving no score/list exposure and a blacklist 401 path. Then start the actual app with the cached JDK and drive equivalent happy/failure requests with `curl.exe`, using an ephemeral locally generated test JWT and no real Kakao/Upstage calls. Run all tests and bootJar; preserve unrelated dirty changes.
  Parallelization: Wave 4 | Blocked by: 1-6 | Blocks: final verification
  References (executor has NO interview context - be exhaustive): all prior todos; `src/main/resources/application.yml`; `src/main/resources/data.sql`; `CleanAlbaBackendApplication.java`; Gradle test configuration in `build.gradle`.
  Acceptance criteria (agent-executable): RANDOM_PORT test and live curl transcript both demonstrate the full state transition and exact 201/200/401/403/409 results; `.\gradlew.bat clean test bootJar` exits 0 under cached Temurin 17; no external API is called; `git diff --check` exits 0 and diff contains only approved pipeline plus pre-existing documentation changes.
  QA scenarios (name the exact tool + invocation): execute `$env:JAVA_HOME='C:\Users\ohyou\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2'; .\gradlew.bat clean test bootJar`, run `bootRun` in a background PTY, then `curl.exe` the create/list/detail/approve/repeat/list and rejection/blacklist flows; Evidence `.omo/evidence/task-7-review-moderation-core.txt` and `.omo/evidence/task-7-review-moderation-core-http.txt`.
  Commit: N | user did not request git history changes

## Final verification wave
> Runs in parallel after ALL todos. ALL must APPROVE. Surface results and wait for the user's explicit okay before declaring complete.
- [ ] F1. Plan compliance audit
- [ ] F2. Code quality review
- [ ] F3. Real manual QA
- [ ] F4. Scope fidelity

- F1 must compare every Must have/Must NOT have item against the final diff and HTTP evidence.
- F2 must run the required post-implementation `review-work` and a debugging runtime audit with three evidence-backed hypotheses, plus inspect auth/transaction/concurrency/security boundaries.
- F3 must independently rerun full Gradle/package checks and the live curl happy/failure pipeline; tests-only is insufficient.
- F4 must confirm no attachments/stats/my-reviews/detail-expansion/Kakao/search/AI scope entered the diff and no pre-existing documentation change was reverted.

## Commit strategy

- No commit during implementation unless the user separately requests Git work.
- If later requested, make one atomic feature commit after all four final verifiers approve: `feat(reviews): add moderation and score pipeline`.
- Never stage unrelated user changes blindly; enumerate exact approved paths first.

## Success criteria

- Every Must have behavior is observable through HTTP with exact status contracts.
- Authenticated review creation cannot spoof author/status/time and always persists PENDING.
- USER cannot access admin routes; invalid, expired, malformed, or blacklisted tokens cannot access protected routes.
- Admin queue/detail are deterministic, paged, and free of entity/lazy-proxy leakage.
- Moderation is one-way and concurrency-safe; duplicate work returns 409.
- Approval and clean-score update are atomic; only APPROVED reviews affect the average.
- First approval changes workspace visibility in the existing score-sorted map list; rejection does not.
- Targeted tests, full tests, `bootJar`, `git diff --check`, and live curl QA all pass under cached Temurin 17.
- Existing documentation/comment edits remain preserved and no excluded feature is added.
