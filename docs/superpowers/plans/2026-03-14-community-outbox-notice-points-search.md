# Community Outbox Projections (Notice/Points/Search) Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make notice / points / search projections “必达 + 可重试” by persisting side effects into `community.outbox_event` within the business transaction and processing them asynchronously with retries; projection failures must not affect HTTP responses nor break HTTP idempotency.

**Architecture:** Replace synchronous `@TransactionalEventListener(AFTER_COMMIT)` projections with `@TransactionalEventListener(BEFORE_COMMIT)` outbox enqueuers (same DB tx) + a scheduled outbox worker (lease/claim/retry). Disable the old synchronous listeners when outbox is enabled to avoid double side effects.

**Tech Stack:** Spring Boot, Spring Transactional Events, `JdbcTemplate`, `@Scheduled`, H2 (tests), MyBatis services for notice/points, Elasticsearch repository for search.

---

## Task 1: Outbox core infra (DB-backed queue)

**Files:**
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/infra/outbox/OutboxEvent.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/infra/outbox/OutboxEventStatus.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/infra/outbox/OutboxProperties.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/infra/outbox/JdbcOutboxEventStore.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/infra/outbox/OutboxWorker.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/infra/outbox/JdbcOutboxEventStoreTest.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/infra/outbox/OutboxWorkerRetryTest.java`

- [x] **Step 1: Write failing tests for claim + retry**
  - Insert one `PENDING` row into `outbox_event`.
  - Worker claims it (`PROCESSING` + lease), handler throws -> row becomes `PENDING` again with `retry_count=1` and `next_retry_at > now`.

- [x] **Step 2: Run tests to verify RED**
  - Run: `cd backend && mvn -pl community-bootstrap test -Dtest=JdbcOutboxEventStoreTest,OutboxWorkerRetryTest`
  - Expected: FAIL (missing classes).

- [x] **Step 3: Implement minimal JDBC store + worker**
  - `enqueue(...)`
  - `findDuePending(limit, now)`
  - `tryClaimProcessing(id, leaseUntil, now)`
  - `markSucceeded(id, now)`
  - `markFailedAndScheduleRetry(id, now, nextRetryAt, error)`
  - `recoverExpiredLeases(now)`

- [x] **Step 4: Run tests to verify GREEN**

## Task 2: Enqueue outbox events on BEFORE_COMMIT

**Files:**
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/message/event/NoticeOutboxEnqueuer.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/event/PointsOutboxEnqueuer.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/search/event/PostOutboxEnqueuer.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/message/event/NoticeProjectionListener.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/event/PointsProjectionListener.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/search/event/PostProjectionListener.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/message/event/NoticeOutboxEnqueuerTest.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/user/event/PointsOutboxEnqueuerTest.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/search/event/PostOutboxEnqueuerTest.java`

- [x] **Step 1: Write failing tests for enqueuers**
  - Given a `ContentLocalEvent` / `SocialLocalEvent`, verify `JdbcOutboxEventStore.enqueue(...)` called with:
    - `event_id = <localEventId>:notice|points|search`
    - `topic = projection.notice|projection.points|projection.search.post`
    - `event_key = toUserId|userId|postId`

- [x] **Step 2: Implement enqueuers and disable sync listeners**
  - Enqueuers: `@TransactionalEventListener(phase = BEFORE_COMMIT)` + `@ConditionalOnProperty(events.outbox.enabled=true)`
  - Sync listeners: `@ConditionalOnProperty(events.outbox.enabled=false, matchIfMissing=true)`

- [x] **Step 3: Run tests**

## Task 3: Outbox handlers (notice / points / search)

**Files:**
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/message/event/NoticeOutboxHandler.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/event/PointsOutboxHandler.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/search/event/PostOutboxHandler.java`
- Modify: `backend/community-bootstrap/src/test/java/com/nowcoder/community/bootstrap/arch/NoDistributedProjectionInfraArchTest.java`
- Modify: `docs/SYSTEM_DESIGN.md`
- Modify: `backend/community-bootstrap/src/main/resources/application.yml`

- [x] **Step 1: Write failing tests for handler dispatch**
  - Worker sees `topic=projection.points` -> calls `PointsService.applyPoints(...)`.
  - Worker sees `topic=projection.notice` -> calls `NoticeService.createNotice(...)`.
  - Worker sees `topic=projection.search.post` -> calls `PostSearchRepository.delete/upsert(...)`.

- [x] **Step 2: Implement handlers**
  - **Notice:** payload includes `{toUserId, topic, eventId, type, payload}`; worker serializes message content JSON and writes via `NoticeService`.
  - **Points:** payload includes `{userId, delta, eventId, type}`; worker calls `PointsService.applyPoints(...)` (already idempotent).
  - **Search:** payload includes `{postId}`; worker reads current post state and either deletes or upserts.

- [x] **Step 3: Update arch test + docs + config**
  - Allow local `infra/outbox` in runtime; keep Kafka/RPC-outbox artifacts forbidden.
  - Add `events.outbox.enabled: true` in main `application.yml` (tests keep it `false` by default).
  - Update `docs/SYSTEM_DESIGN.md` to reflect local outbox for reliability.

- [x] **Step 4: Run full module tests**
  - Run: `cd backend && mvn -pl community-bootstrap test`
