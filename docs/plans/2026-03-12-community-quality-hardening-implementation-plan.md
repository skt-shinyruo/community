# Community Quality Hardening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make `commentCount` include replies (no backfill) and fix a set of correctness/reliability/observability/performance issues across backend + IM realtime + frontend.

**Architecture:** Keep changes localized and backward compatible where possible (e.g., legacy post-score set fallback). Prefer existing batch endpoints and existing rollback/idempotency patterns.

**Tech Stack:** Spring Boot 3 / Java 17, MyBatis, Redis, Reactor (WebFlux), Vue 3 + Vite + Pinia.

---

### Task 1: Unify `commentCount` semantics (include replies)

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/service/CommentService.java`
- Modify: `frontend/src/views/PostDetailView.vue`

**Step 1: Update backend write path**
- Change `CommentService.addComment(...)` so both top-level comments and replies:
  - increment `postService.incrementCommentCount(postId, 1)`
  - enqueue `postScoreQueue.add(postId)` after commit

**Step 2: Update frontend wording and refresh behavior**
- Change post detail header to “回复 N” (since count now includes replies).
- After a successful reply submission, refresh post summary (`loadPost()`), so `commentCount` updates immediately.

**Step 3: Run backend tests**
- Run: `mvn -pl backend/community-bootstrap test`
- Expected: PASS

### Task 2: Fix block idempotency and rollback safety

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/social/block/BlockService.java`

**Step 1: Publish only on state change**
- Use `repository.block/unblock` return value; if `false`, return early without publishing.

**Step 2: Add best-effort rollback**
- Register rollback on transaction rollback.
- On event publish failure, rollback the repository state and rethrow.

**Step 3: Run backend tests**
- Run: `mvn -pl backend/community-bootstrap test`
- Expected: PASS

### Task 3: Make prod auth startup validation effective for `community-app`

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/auth/config/AuthStartupValidator.java`

**Step 1: Adjust gating**
- Allow validation when `spring.application.name` is either `auth-service` or `community-app`.

**Step 2: Compile**
- Run: `mvn -pl backend/community-bootstrap -DskipTests test`
- Expected: BUILD SUCCESS

### Task 4: Improve exception observability (cause + logging)

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/exception/BusinessException.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/infra/web/GlobalExceptionHandler.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/infra/internalclient/InternalClientSupport.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/LocalAvatarStorageProvider.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/R2AvatarStorageProvider.java`

**Step 1: Add `cause` constructors**
- Add `BusinessException(ErrorCode, Throwable)` and `BusinessException(ErrorCode, String, Throwable)`.

**Step 2: Update wrappers to preserve causes**
- Ensure internal client wrappers and avatar storage providers pass the original exception as cause.

**Step 3: Log 5xx BusinessException**
- In `GlobalExceptionHandler`, log stack traces for `BusinessException` where `httpStatus >= 500`.

**Step 4: Run backend tests**
- Run: `mvn -pl backend/community-bootstrap test`
- Expected: PASS

### Task 5: Post score queue backoff to prevent retry spin

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/score/PostScoreQueue.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/score/RedisPostScoreQueue.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/score/InMemoryPostScoreQueue.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/score/PostScoreRefresher.java`

**Step 1: Extend queue interface**
- Add default methods: `reenqueue(postId)` and `onSuccess(postId)`.

**Step 2: Implement Redis delayed reenqueue**
- Use ZSET for scheduling by availableAt.
- Track retry attempts and compute exponential backoff with cap + jitter.
- Keep legacy Set pop fallback to avoid losing old queued items.

**Step 3: Update refresher**
- On success: call `scoreQueue.onSuccess(postId)`.
- On failure: call `scoreQueue.reenqueue(postId)`.

**Step 4: Run backend tests**
- Run: `mvn -pl backend/community-bootstrap test`
- Expected: PASS

### Task 6: IM WS lifecycle-safe bootstrap

**Files:**
- Modify: `backend/im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImWebSocketHandler.java`
- Modify: `backend/im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/WsConnection.java`

**Step 1: Track bootstrap subscription**
- Store `Disposable` in `WsConnection`.
- Dispose it in cleanup before removing rooms.

**Step 2: Run IM realtime tests**
- Run: `mvn -pl backend/im/im-realtime test`
- Expected: PASS

### Task 7: Search highlight improvements (case-insensitive, multi-term)

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/search/repo/ElasticsearchPostSearchRepository.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/search/repo/InMemoryPostSearchRepository.java`

**Step 1: Tokenize keyword**
- Split on whitespace, dedup (case-insensitive), cap token count/length.

**Step 2: Highlight with combined regex**
- Build a single `Pattern` over tokens, apply case-insensitive replacement with `<em>...</em>`.

**Step 3: Make in-memory matching case-insensitive**
- Use lowercase contains checks.

**Step 4: Run backend tests**
- Run: `mvn -pl backend/community-bootstrap test`
- Expected: PASS

### Task 8: Frontend post detail hydration (remove N+1)

**Files:**
- Modify: `frontend/src/views/PostDetailView.vue`
- Modify: `frontend/src/components/ui/UiUserCard.vue`

**Step 1: Batch hydrate comments**
- Collect commentIds/userIds and call:
  - `postMetaCache.ensureUserSummaries(...)`
  - `postMetaCache.ensureLikeCounts(2, ...)`
  - `postMetaCache.ensureLikeStatuses(2, ...)` (if authed)

**Step 2: Batch hydrate replies**
- Collect replyIds/userIds (including targetId users) and apply the same batch hydration.

**Step 3: Lazy-upgrade `UiUserCard` data**
- If `user.createTime` (or other rich fields) are missing, fetch `getUserProfile` on hover and use it for the popover.

**Step 4: Run frontend tests**
- Run: `cd frontend && npm test`
- Expected: PASS

