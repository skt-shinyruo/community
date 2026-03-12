# Community Quality Hardening Design

**Date:** 2026-03-12

## Goal

Fix correctness/reliability/observability/performance issues in the current monolith + IM realtime + frontend, with minimal interface churn and without large migrations.

## Non-goals

- Backfilling historical `discuss_post.comment_count` values.
- Large infra additions (outbox, delayed queues service, ES highlighter refactor to native highlight fields, etc.).

## Decisions

### `commentCount` semantics

`DiscussPost.commentCount` / `discuss_post.comment_count` is defined as:

> **Total discussion count** = **top-level comments + replies** created under the post.

**Historical data is not backfilled**. Existing posts may have a smaller `comment_count` than the true total until new comments/replies occur.

## Planned Changes

### 1) `commentCount` includes replies (write-path + UI + score refresh)

- Backend write path increments `comment_count` for both:
  - top-level comments (`entityType=POST`)
  - replies (`entityType=COMMENT`)
- Replies also enqueue post score refresh after commit, since score depends on `commentCount`.
- Frontend wording aligns:
  - Post list already labels the column as “回复”.
  - Post detail header changes from “评论 N” to “回复 N”.
- Post detail reply submit refreshes post summary so the count updates immediately after replying.

**Files (expected):**
- `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/service/CommentService.java`
- `frontend/src/views/PostDetailView.vue`

### 2) Block (拉黑) idempotency + consistency

Problems:
- `BlockService` publishes events even when repository reports no state change.
- In Redis storage mode, failures after writing could leave state changed but request failed.

Fix:
- Only publish `BlockRelationChanged` when `repository.block/unblock` returns `true`.
- Add rollback on:
  - transaction rollback (best-effort)
  - event publish failure (best-effort)

**Files (expected):**
- `backend/community-bootstrap/src/main/java/com/nowcoder/community/social/block/BlockService.java`

### 3) Prod startup validation actually runs in `community-app`

Problem:
- `AuthStartupValidator` only runs for `spring.application.name=auth-service`, but the monolith is `community-app`.

Fix:
- Run auth prod validations for both `auth-service` and `community-app`.

**Files (expected):**
- `backend/community-bootstrap/src/main/java/com/nowcoder/community/auth/config/AuthStartupValidator.java`

### 4) Observability: preserve causes + log 5xx BusinessException

Problems:
- Many wrappers throw `BusinessException(INTERNAL_ERROR, "...")` without preserving the original exception cause.
- `GlobalExceptionHandler` does not log `BusinessException` stacks, making root-cause debugging hard.

Fix:
- Add `BusinessException(..., cause)` constructors.
- Update key wrappers to pass causes (internal calls, avatar storage providers).
- Log `BusinessException` with `httpStatus >= 500` in `GlobalExceptionHandler` (include traceId).

**Files (expected):**
- `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/exception/BusinessException.java`
- `backend/community-bootstrap/src/main/java/com/nowcoder/community/infra/web/GlobalExceptionHandler.java`
- `backend/community-bootstrap/src/main/java/com/nowcoder/community/infra/internalclient/InternalClientSupport.java`
- `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/LocalAvatarStorageProvider.java`
- `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/R2AvatarStorageProvider.java`

### 5) Post score refresh queue: avoid immediate retry spin

Problem:
- On refresh failure, current implementation re-enqueues immediately into a Redis Set, which can be re-popped in the same batch and spin on persistent failures.

Fix:
- Extend `PostScoreQueue` with:
  - `reenqueue(postId)` for failure path (with backoff in Redis implementation)
  - `onSuccess(postId)` to reset backoff state
- Redis implementation uses a ZSET for “availableAt” scheduling, plus a retry counter to compute backoff.
- Keep a best-effort fallback to legacy Set pop to avoid dropping old queued items.

**Files (expected):**
- `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/score/PostScoreQueue.java`
- `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/score/RedisPostScoreQueue.java`
- `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/score/InMemoryPostScoreQueue.java`
- `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/score/PostScoreRefresher.java`

### 6) IM realtime: bind bootstrap subscription to WS lifecycle

Problem:
- Room bootstrap uses `subscribe(...)` without disposing on connection close, risking leaks and stale local index entries.

Fix:
- Store the subscription disposable on `WsConnection` and dispose it during cleanup.

**Files (expected):**
- `backend/im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImWebSocketHandler.java`
- `backend/im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/WsConnection.java`

### 7) Search highlight: improve matching/highlight quality

Problem:
- Highlight is `text.replace(keyword, "<em>...")`, case-sensitive and doesn’t handle multi-term queries.

Fix:
- Tokenize keyword and apply case-insensitive highlighting using a combined regex.
- In-memory search matching becomes case-insensitive to align with highlight behavior.

**Files (expected):**
- `backend/community-bootstrap/src/main/java/com/nowcoder/community/search/repo/ElasticsearchPostSearchRepository.java`
- `backend/community-bootstrap/src/main/java/com/nowcoder/community/search/repo/InMemoryPostSearchRepository.java`

### 8) Frontend Post detail: remove N+1 calls (users/likes)

Problem:
- Comment/reply hydration performs per-item calls for user profile + like count + like status.

Fix:
- Use existing batch endpoints and `postMetaCache`:
  - `/api/users/batch-summary`
  - `/api/likes/counts`, `/api/likes/statuses`
- `UiUserCard` lazily upgrades a summary user to full profile on hover to keep the popover rich without reintroducing N+1 on initial page load.

**Files (expected):**
- `frontend/src/views/PostDetailView.vue`
- `frontend/src/components/ui/UiUserCard.vue`

## Testing Strategy

- Backend:
  - `mvn -pl backend/community-bootstrap test`
  - `mvn -pl backend/im/im-realtime test`
- Frontend:
  - `cd frontend && npm test` (or `pnpm test` depending on repo tooling)

## Rollout Notes

- `commentCount` semantics change affects ranking (post score): replies will now increase score.
- No historical backfill means some existing posts may appear “under-counted” until new activity occurs.

