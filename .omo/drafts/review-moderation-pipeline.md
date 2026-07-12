# Review Moderation Pipeline Draft

- intent: clear
- review_required: false
- status: awaiting-approval
- pending_action: write `.omo/plans/review-moderation-pipeline.md`
- dirty_worktree: existing comment/documentation changes must be preserved

## Objective

Implement the vertical backend flow: authenticated review creation as PENDING, administrator review queue/detail, one-way approval or rejection, transactional clean-score recalculation, and observable reflection in the existing workspace list.

## Components

1. Review submission: authenticated user creates a PENDING review for an existing workspace.
2. Authorization boundary: protected review/admin routes validate signature, expiry, blacklist, and role; author email comes only from JWT subject.
3. Admin moderation: ADMIN can list/filter pending reviews, inspect details, and transition PENDING to APPROVED or REJECTED once.
4. Score transaction: APPROVED transition and workspace clean-score recalculation commit or roll back together.
5. Observable result: first approved review makes a previously unscored workspace appear in GET /workspaces; rejected reviews never affect the score.
6. Verification: tests-first domain, repository, service transaction, MockMvc authorization, and live HTTP happy/failure QA.

## Evidence

- `Review.java`: checklist fields, authorEmail, PENDING default, and score formula exist; creation and transition methods do not.
- `ReviewRepository.java`: only workspace/status lookup exists.
- `WorkspaceService.java`: approved-review average recalculation already exists.
- `WorkspaceRepository.java`: GET /workspaces excludes null scores and sorts score descending.
- `SecurityConfig.java` and `JwtAuthFilter.java`: current filter only covers `/api/*` and only checks blacklist, so new protected routes need a real centralized authentication boundary.
- `CorsConfig.java`: PATCH is not currently allowed.
- Existing tests contain only `contextLoads`.

## Recommended scope

### Include

- `POST /workspaces/{workspaceId}/reviews`
- `GET /admin/reviews?status=PENDING&page=0&size=20`
- `GET /admin/reviews/{reviewId}`
- `PATCH /admin/reviews/{reviewId}/status`
- PENDING-only one-way transitions; repeated processing returns 409
- same-transaction approval and clean-score recalculation
- centralized JWT/blacklist/role checks for the new protected routes
- PATCH CORS support
- deterministic pagination/order and tests-first verification

### Exclude from this first vertical slice

- S3/file attachments
- admin stats dashboard
- my-review list
- workspace detail/summary statistic expansion
- public manual recalculation endpoint
- Kakao route renaming
- recommender, contract analyzer, search extensions, and job postings

## Proposed decisions awaiting approval

1. Use the BE contract `POST /workspaces/{id}/reviews`, not the conflicting frontend shorthand `POST /reviews`.
2. Allow only `PENDING -> APPROVED|REJECTED`; no reopening and no rejection reason in this slice.
3. Use page/size pagination with newest-first ordering for the admin queue.
4. Keep attachments and dashboard stats out of the core slice so the score-reflection pipeline ships first.
5. Use tests-first implementation and verify with Gradle plus a live HTTP happy/failure scenario using the available cached Temurin 17 JDK.

## Approval gate

Approve the recommended scope and decisions above before generating the executable plan. Approval authorizes plan generation only; implementation starts after the generated plan is explicitly started.
