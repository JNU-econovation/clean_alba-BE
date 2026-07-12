---
slug: review-moderation-core
status: approved-for-plan
intent: clear
pending-action: write .omo/plans/review-moderation-core.md
approach: tests-first vertical slice from authenticated review submission through one-way admin moderation and atomic clean-score reflection
---

# Draft: review-moderation-core

## Components (topology ledger)

| id | outcome | status | evidence path |
| --- | --- | --- | --- |
| C1 | Authenticated user submits a review that is always PENDING | active | `domain/Review.java`, `controller/ReviewController.java` |
| C2 | Protected routes enforce strict Bearer validation, blacklist, subject, and role | active | `util/JwtAuthFilter.java`, `config/SecurityConfig.java` |
| C3 | ADMIN lists and inspects pending reviews with deterministic pagination | active | `repository/ReviewRepository.java` |
| C4 | ADMIN performs exactly one PENDING to APPROVED/REJECTED transition | active | `domain/ReviewStatus.java` |
| C5 | Approval and clean-score recalculation commit or roll back together | active | `service/WorkspaceService.java` |
| C6 | Existing workspace list visibly reflects the first approval | active | `repository/WorkspaceRepository.java` |

## Open assumptions (announced defaults)

| assumption | adopted default | rationale | reversible? |
| --- | --- | --- | --- |
| Review endpoint conflict | `POST /workspaces/{id}/reviews` only | approved BE contract | yes |
| Moderation transition | PENDING to APPROVED or REJECTED only; repeat is 409 | prevents accidental rescoring | yes |
| Queue paging | page 0, size 20, maximum 50, createdAt/reviewId descending | bounded and deterministic | yes |
| Author identity | JWT subject email | only stable identity currently present | yes, requires future user model |
| Attachments and dashboard | excluded | not required for core score-reflection slice | yes |

## Findings (cited - path:lines)

- `src/main/java/com/cleanmap/clean_alba_backend/domain/Review.java:18-78`: fields and score exist; creation and moderation transition do not.
- `src/main/java/com/cleanmap/clean_alba_backend/repository/ReviewRepository.java:10-14`: only approved-review lookup exists.
- `src/main/java/com/cleanmap/clean_alba_backend/service/WorkspaceService.java:75-89`: approved-review averaging already updates the workspace.
- `src/main/java/com/cleanmap/clean_alba_backend/repository/WorkspaceRepository.java:17-28`: null scores are hidden and results sort descending.
- `src/main/java/com/cleanmap/clean_alba_backend/util/JwtAuthFilter.java:28-42`: current filter checks blacklist only.
- `src/main/java/com/cleanmap/clean_alba_backend/config/SecurityConfig.java:17-21`: current filter registration covers `/api/*` only.
- `src/main/java/com/cleanmap/clean_alba_backend/config/CorsConfig.java:13-21`: PATCH is absent.
- `src/test/java/com/cleanmap/clean_alba_backend/CleanAlbaBackendApplicationTests.java`: context load is the only test.

## Decisions (with rationale)

- Do not add Spring Security or another dependency; extend the existing filter only for exact new protected routes and pass verified email/role as request attributes.
- Preserve existing `/api/*` blacklist behavior and existing `POST /workspaces` behavior.
- Use boxed Boolean checklist fields at the JSON boundary and reject omissions with 400; never accept author/status/time from the client.
- Use pessimistic locks in review then workspace order, flush the new status, and recalculate inside one outer transaction.
- Return DTOs only; never serialize JPA entities or lazy associations.
- Use tests-first development and a real embedded HTTP test plus live curl QA.

## Scope IN

- `POST /workspaces/{workspaceId}/reviews`
- `GET /admin/reviews?status=pending&page=0&size=20`
- `GET /admin/reviews/{reviewId}`
- `PATCH /admin/reviews/{reviewId}/status`
- strict auth for these routes, PATCH CORS, atomic score reflection, regression tests

## Scope OUT (Must NOT have)

- attachments/S3, admin stats, my reviews, workspace detail expansion, public recalculate endpoint
- Kakao route renaming, search/recommendation/contract analysis/job posting changes
- Spring Security dependency or user table migration

## Open questions

- None. User approved all announced defaults in `.omo/drafts/review-moderation-pipeline.md`.

## Approval gate
status: approved
<!-- When exploration is exhausted and unknowns are answered, set status: awaiting-approval. -->
<!-- That durable record is the loop guard: on a later turn read it and resume at the gate instead of re-running exploration. -->
