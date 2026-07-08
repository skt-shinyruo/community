# BBS Reliability P2 Core Slices Implementation Plan

> This plan is for the P2 core business slices only. Do not implement until the
> spec and plan are reviewed and explicitly approved.

## Goal

Close the P2 reliability loop for:

1. post/comment writes;
2. like/follow writes;
3. post detail and hot-feed reads.

The work should mostly add focused acceptance tests around existing runtime
capabilities, plus the minimal read-path fail-open fixes identified in the P2
spec.

## Architecture Constraints

- Follow the repository's strict DDD Tactical Layering.
- Inbound adapters may only call same-domain application services.
- Application services must not depend directly on MyBatis mapper/dataobject
  types or HTTP transport types.
- Domain code must not depend on Spring, infrastructure, application, or
  foreign-domain APIs.
- Cross-domain synchronous collaboration must use owner-domain `api.query` or
  `api.action`; asynchronous collaboration must use owner-domain
  `contracts.event`.
- Do not add new P1 governance APIs or P3 cache/performance optimizations.

## File Structure

Expected modifications:

```text
docs/handbook/reliability.md
backend/community-app/src/main/java/com/nowcoder/community/content/application/PostReadApplicationService.java
backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedReadApplicationService.java
backend/community-app/src/test/java/com/nowcoder/community/content/application/PostPublishingApplicationServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/content/application/PostReadApplicationServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/content/application/FeedReadApplicationServiceReliabilityTest.java
backend/community-app/src/test/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/OutboxContentEventPublisherTest.java
backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/ContentEventKafkaOutboxHandlerTest.java
backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/OutboxSocialDomainEventPublisherTest.java
backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/SocialEventKafkaOutboxHandlerTest.java
backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/growth/application/TaskProgressApplicationServiceTest.java
```

If an existing test class becomes too broad, add a new narrowly named test class
under the same package instead of mixing unrelated assertions into one test.

## Task 1: Content Write Idempotency Acceptance

Reliability loop verification.

Files:

- `PostPublishingApplicationServiceTest`
- `CommentApplicationServiceTest`

Steps:

1. Add or tighten a test where the same `Idempotency-Key` and same command is
   replayed for post creation. Assert only one content fact is persisted and the
   replay returns the recorded result.
2. Add or tighten a test where the same key is reused with changed post command
   content. Assert the application rejects the conflicting fingerprint.
3. Repeat the equivalent acceptance for comment creation.
4. Keep the test at the application boundary. Use existing fakes or test
   helpers; do not reach into controllers or infrastructure unless already part
   of the test fixture.

Verification:

```bash
cd backend
mvn test -pl :community-app -Dtest='PostPublishingApplicationServiceTest,CommentApplicationServiceTest'
```

## Task 2: Content Eventbus and Replay Boundary Verification

Reliability loop verification.

Files:

- `OutboxContentEventPublisherTest`
- `ContentEventKafkaOutboxHandlerTest`
- `SearchPostProjectionApplicationServiceTest`
- `NoticeProjectionApplicationServiceTest`
- `TaskProgressApplicationServiceTest`

Steps:

1. Assert content contract payloads written to `eventbus.content` include stable
   source event identity and owner fact fields required by downstream
   projections.
2. Assert the Kafka outbox handler republishes the stored payload without
   changing source identity.
3. Add focused projection tests for replayed/duplicate content events:
   search does not regress indexed post metadata, notice does not duplicate a
   notification, and growth does not duplicate task progress.
4. Document in test names or assertions that common-outbox DEAD replay applies
   to outbox rows, while arbitrary Kafka consumer failures require consumer
   retry/DLQ behavior outside this P2 implementation.

Verification:

```bash
cd backend
mvn test -pl :community-app -Dtest='OutboxContentEventPublisherTest,ContentEventKafkaOutboxHandlerTest,SearchPostProjectionApplicationServiceTest,NoticeProjectionApplicationServiceTest,TaskProgressApplicationServiceTest'
```

## Task 3: Social Write and Projection Acceptance

Reliability loop verification.

Files:

- `OutboxSocialDomainEventPublisherTest`
- `SocialEventKafkaOutboxHandlerTest`
- `NoticeProjectionApplicationServiceTest`
- `TaskProgressApplicationServiceTest`
- `PostHotFeedProjectionApplicationServiceTest`

Steps:

1. Assert social contract payloads written to `eventbus.social` carry stable
   source event identity, relation keys, actor, target, and state change
   semantics for like/follow events.
2. Assert social Kafka outbox dispatch preserves the stored source identity.
3. Add duplicate social event tests for notice and growth projections.
4. Add or tighten growth rollback coverage for unlike and unfollow reversal
   semantics where current behavior supports them.
5. Add or tighten hot-feed projection tests proving stale or out-of-order social
   updates do not overwrite newer rank/counter state.
6. If a behavior gap appears in social application code, keep the fix inside the
   social application/domain/infrastructure owner boundary and do not add new
   cross-domain shortcuts.

Verification:

```bash
cd backend
mvn test -pl :community-app -Dtest='OutboxSocialDomainEventPublisherTest,SocialEventKafkaOutboxHandlerTest,NoticeProjectionApplicationServiceTest,TaskProgressApplicationServiceTest,PostHotFeedProjectionApplicationServiceTest'
```

## Task 4: Post Detail Cache Fail-Open

Behavior implementation gap plus reliability loop verification.

Files:

- `PostReadApplicationService`
- `PostReadApplicationServiceTest`

Steps:

1. Add a failing test where detail shell cache read throws. Assert
   `getPostDetail(...)` loads source data and still applies counter and viewer
   overlays.
2. Add a failing test where detail shell cache write-back throws after source
   load. Assert the response still succeeds and contains the same overlay data.
3. Implement small application-level helpers around the existing detail cache
   read and write calls.
4. Do not move Redis concerns into the controller or domain. Do not add a new
   cache abstraction unless an existing application-owned port already requires
   it.

Implementation shape:

```java
private Optional<PostDetailResult> safeGetDetailCache(UUID postId) {
    try {
        return postDetailCache.get(postId);
    } catch (RuntimeException ex) {
        return Optional.empty();
    }
}

private void safePutDetailCache(UUID postId, PostDetailResult detail) {
    try {
        postDetailCache.put(postId, detail);
    } catch (RuntimeException ex) {
        // Detail cache writes are best-effort for read responses.
    }
}
```

Adjust names and return types to the existing `PostReadApplicationService`
cache port. Keep overlays outside the cached viewer-neutral shell.

Verification:

```bash
cd backend
mvn test -pl :community-app -Dtest='PostReadApplicationServiceTest'
```

## Task 5: Hot Feed Fallback Cache Write Fail-Open

Behavior implementation gap plus reliability loop verification.

Files:

- `FeedReadApplicationService`
- `FeedReadApplicationServiceReliabilityTest`

Steps:

1. Add a failing test where hot-feed cache read misses or fails, source fallback
   loads posts, and feed cache warm-up throws. Assert the response still
   returns fallback items and rank metadata.
2. Add a failing test where summary cache backfill throws during fallback.
   Assert the response still succeeds with assembled summaries and counters.
3. Wrap fallback cache warm-up and summary cache backfill in best-effort helper
   methods.
4. Preserve existing `HotFeedReadMetrics` behavior for cache hit/miss/fallback
   paths. Do not add new metrics or P3 cache controls.

Implementation shape:

```java
private void safeWarmFeedCache(List<DiscussPost> posts, UUID boardId, String rankVersion) {
    try {
        warmFeedCache(posts, boardId, rankVersion);
    } catch (RuntimeException ex) {
        // Feed cache warm-up is best-effort for fallback reads.
    }
}

private void safePutSummaryCache(List<PostSummaryResult> items) {
    try {
        postSummaryCache.putAll(items);
    } catch (RuntimeException ex) {
        // Summary cache backfill is best-effort for fallback reads.
    }
}
```

Adjust method signatures to existing types in `FeedReadApplicationService`.

Verification:

```bash
cd backend
mvn test -pl :community-app -Dtest='FeedReadApplicationServiceReliabilityTest'
```

## Task 6: Reliability Handbook Alignment

Documentation alignment.

File:

- `docs/handbook/reliability.md`

Steps:

1. Update the content idempotency section so post/comment fingerprint semantics
   match the current application services.
2. Add a concise P2 slice table for content writes, social writes, and read
   paths.
3. State that `eventbus.content` and `eventbus.social` are the owner-domain
   eventbus outbox topics used by these slices.
4. State that common-outbox DEAD replay requeues outbox rows, while Kafka
   consumer retry/DLQ is a separate responsibility unless a downstream
   projection persists its own outbox row.
5. Do not document unimplemented P1/P3 capabilities.

Verification:

```bash
git diff --check -- docs/handbook/reliability.md
```

## Task 7: Final Verification

Run focused P2 tests:

```bash
cd backend
mvn test -pl :community-app -Dtest='PostPublishingApplicationServiceTest,CommentApplicationServiceTest,OutboxContentEventPublisherTest,ContentEventKafkaOutboxHandlerTest,SearchPostProjectionApplicationServiceTest,NoticeProjectionApplicationServiceTest,TaskProgressApplicationServiceTest,OutboxSocialDomainEventPublisherTest,SocialEventKafkaOutboxHandlerTest,PostHotFeedProjectionApplicationServiceTest,PostReadApplicationServiceTest,FeedReadApplicationServiceReliabilityTest'
```

Run architecture tests because the planned implementation touches application
and infrastructure test boundaries:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Run whitespace validation:

```bash
git diff --check
```

Completion criteria:

- All focused P2 tests pass.
- Architecture tests pass.
- The handbook documents only implemented or planned P2 behavior.
- No controller, listener, handler, bridge, enqueuer, or job bypasses the
  same-domain application boundary.
- No new P1 governance surface or P3 optimization is introduced.
