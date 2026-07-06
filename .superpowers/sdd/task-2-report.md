## Task 2 Report: Hot Feed Projection Hardening

### What I implemented

- Added `ContentFeedPolicyProperties` under `content.feed` with:
  - `hotRankVersion` defaulting to `hot-v2`
  - `latestFallbackEnabled` defaulting to `true`
- Changed `ProjectPostHotFeedCommand` to carry:
  - `postId`
  - `boardId`
  - `signalWeight`
  - `sourceEventId`
  - `sourceVersion`
- Hardened `PostHotFeedProjectionApplicationService` to:
  - persist the configured rank version into feed cache on each projection
  - evict hidden or deleted posts from feed projections
  - use the configured rank version for global and board hot-feed upserts
- Hardened `FeedReadApplicationService` to:
  - return cache-backed rank version from `FeedPageResult`
  - warm fallback hot-feed cache entries with the configured rank version
  - preserve the existing constructor shape via an overload while supporting injected policy properties
- Extended `PostFeedCache` with `writeRankVersion` and `readRankVersion`
- Implemented rank-version storage in `RedisPostFeedCache`
- Updated `PostHotFeedProjectionKafkaListener` to map backbone metadata version into the projection command
- Updated `application.yml` with `content.feed.hot-rank-version` and `content.feed.latest-fallback-enabled`

### Incidental compatibility updates

- Updated `PostHotFeedProjectionLocalListener` and its test to match the new command shape and event metadata so the module still compiles cleanly.

### TDD evidence

#### RED

Command:

```bash
cd backend && mvn test -pl :community-app -Dtest=PostHotFeedProjectionApplicationServiceTest,FeedReadApplicationServiceTest,PostHotFeedProjectionKafkaListenerTest,RedisPostFeedCacheTest
```

Observed failure summary:

- test compile failed before implementation
- missing `ContentFeedPolicyProperties`
- missing `PostFeedCache.readRankVersion()` / `writeRankVersion()`
- old `ProjectPostHotFeedCommand` constructor and accessors no longer matched Task 2 expectations
- listener tests failed on missing `sourceVersion`

#### GREEN

Command:

```bash
cd backend && mvn test -pl :community-app -Dtest=PostHotFeedProjectionApplicationServiceTest,FeedReadApplicationServiceTest,PostHotFeedProjectionKafkaListenerTest,RedisPostFeedCacheTest
```

Observed success summary:

- `Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`

### Exact tests run and results

- `PostHotFeedProjectionApplicationServiceTest`: PASS
- `FeedReadApplicationServiceTest`: PASS
- `PostHotFeedProjectionKafkaListenerTest`: PASS
- `RedisPostFeedCacheTest`: PASS

### Files changed

- `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentFeedPolicyProperties.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/ProjectPostHotFeedCommand.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedReadApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostFeedCache.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionLocalListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCache.java`
- `backend/community-app/src/main/resources/application.yml`
- `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/application/FeedReadApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListenerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionLocalListenerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCacheTest.java`
- `docs/superpowers/plans/2026-07-06-community-content-platform-high-concurrency-implementation-plan.md`

### Self-review findings

- The rank version now comes from Redis-backed cache reads and falls back to `hot-v2` inside `RedisPostFeedCache` when absent.
- Hidden posts are treated consistently with deleted posts in the projection path by evicting feed entries instead of recomputing score.
- The new feed policy property currently uses `latestFallbackEnabled` only as configuration surface; Task 2 did not require wiring behavior behind that flag.

### Issues or concerns

- None for Task 2 scope.

## Follow-up fix: hot-feed fallback rank-version drift

### What I changed

- Added a regression test covering a non-default `ContentFeedPolicyProperties.hotRankVersion` during cold-cache fallback.
- Changed `FeedReadApplicationService` so the fallback hot-feed path:
  - writes the configured rank version into `PostFeedCache`
  - returns that warmed rank version in `FeedPageResult`
- Kept the cache-hit path using the cache-backed rank version read.

### Why

The previous fallback path warmed entries with the configured version but reported `postFeedCache.readRankVersion()`. When Redis had no rank-version key or a stale one, the page payload could disagree with the cache entries just written.

### Test evidence

Red regression run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FeedReadApplicationServiceTest
```

Observed failure:

- `FeedReadApplicationServiceTest.listGlobalHotFeedShouldReturnConfiguredRankVersionWhenColdCacheWarmsFeed`
- expected `hot-v9`, but the service returned `hot-v2`

Green verification run:

```bash
cd backend && mvn test -pl :community-app -Dtest=PostHotFeedProjectionApplicationServiceTest,FeedReadApplicationServiceTest,PostHotFeedProjectionKafkaListenerTest,RedisPostFeedCacheTest
```

Observed result:

- `Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`
