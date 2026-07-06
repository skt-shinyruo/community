## Task 3 Report: Follow Feed Pull-Merge Read Model

### What I implemented

Implemented the Task 3 follow-feed read model in the content domain and the supporting synchronous social follow query path:

- Added `FollowFeedCache` and `FollowFeedReadApplicationService` under `content.application`.
- Added `RedisFollowFeedCache` backed by Redis value storage with JSON page payloads keyed by viewer/page/size.
- Extended `FeedController` with `GET /api/feed/follow`, keeping controller logic limited to auth extraction and application-service delegation.
- Extended `PostContentRepository` plus `MyBatisPostContentRepository`, `DiscussPostMapper`, and `discusspost-mapper.xml` with `listRecentVisiblePostsByAuthorIds(...)`.
- Extended `SocialFollowQueryApi`, `SocialFollowQueryApiAdapter`, `FollowApplicationService`, `FollowRepository`, `MyBatisFollowRepository`, `RedisFollowRepository`, and `FollowMapper` with `listFolloweeIds(...)`.
- Added/updated Task 3 tests:
  - `FollowFeedReadApplicationServiceTest`
  - `RedisFollowFeedCacheTest`
  - `FeedControllerTest`
  - `FollowApplicationServiceTest`
  - `SocialFollowQueryApiAdapterTest`
- Updated Task 3 checkboxes in `docs/superpowers/plans/2026-07-06-community-content-platform-high-concurrency-implementation-plan.md`.

### TDD evidence

#### RED

Command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest,FeedControllerTest,FollowApplicationServiceTest,SocialFollowQueryApiAdapterTest
```

Result summary:

- Build failed during test compilation, as expected.
- Missing symbols included:
  - `FollowFeedReadApplicationService`
  - `FollowFeedCache`
  - `SocialFollowQueryApi.listFolloweeIds(...)`
  - `PostContentRepository.listRecentVisiblePostsByAuthorIds(...)`
  - `FollowApplicationService.listFolloweeIds(...)`
  - `FollowRepository.listFolloweeIds(...)`
- This confirmed the tests were exercising the intended Task 3 surface and not passing against preexisting behavior.

#### GREEN

Command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest,RedisFollowFeedCacheTest,FeedControllerTest,FollowApplicationServiceTest,SocialFollowQueryApiAdapterTest
```

Result summary:

- `BUILD SUCCESS`
- `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`

### Exact tests run and results

1. RED command:
   - `cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest,FeedControllerTest,FollowApplicationServiceTest,SocialFollowQueryApiAdapterTest`
   - Result: expected failure due to missing Task 3 types/methods.

2. GREEN / verification command:
   - `cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest,RedisFollowFeedCacheTest,FeedControllerTest,FollowApplicationServiceTest,SocialFollowQueryApiAdapterTest`
   - Result: success, 19 tests passed.

### Files changed

- `backend/community-app/src/main/java/com/nowcoder/community/content/application/FollowFeedCache.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/FollowFeedReadApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisFollowFeedCache.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/FeedController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostContentRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostContentRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/DiscussPostMapper.java`
- `backend/community-app/src/main/resources/mapper/discusspost-mapper.xml`
- `backend/community-app/src/main/java/com/nowcoder/community/social/api/query/SocialFollowQueryApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/api/SocialFollowQueryApiAdapter.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/FollowApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/domain/repository/FollowRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/MyBatisFollowRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/RedisFollowRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/mapper/FollowMapper.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/application/FollowFeedReadApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisFollowFeedCacheTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/controller/FeedControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/social/application/FollowApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/social/application/BlockApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/api/SocialFollowQueryApiAdapterTest.java`
- `docs/superpowers/plans/2026-07-06-community-content-platform-high-concurrency-implementation-plan.md`

### Self-review findings

- The controller boundary stays intact: `FeedController` only extracts auth/cursor params and delegates to same-domain application services.
- Cross-domain sync collaboration stays inside the content application boundary via `SocialFollowQueryApi`.
- Content application code does not depend on MyBatis mapper/dataobject types.
- Follow-feed Redis caching is scoped to page-sized ID lists and preserves the requested page/size in the key.
- The new social query method required interface fallout updates in both the Redis repository implementation and a test-only `FollowRepository` fake.

### Issues or concerns

- `RedisFollowRepository.listFolloweeIds(...)` currently returns raw recent followee IDs from Redis without block filtering, while the MyBatis query path excludes blocked relations. Task 3 verification does not cover `social.storage=redis`, so behavior is consistent for the default DB-backed path but not yet fully aligned across storage modes.

## Controller Fix For Reported Concern

The reported storage-parity concern was real: `FollowApplicationService.listFolloweeIds(...)` returned repository IDs directly, so a Redis-backed repository could leak blocked followees into the follow-feed author list.

### RED

Command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowApplicationServiceTest
```

Result summary:

- `BUILD FAILURE`
- `Tests run: 13, Failures: 1, Errors: 0, Skipped: 0`
- New regression `listFolloweeIdsShouldFilterBlockedRelationsInEitherDirection` failed because blocked followees were returned.

### GREEN

First change made:

- `FollowApplicationService.listFolloweeIds(...)` now filters returned followee IDs through `BlockDomainService.isEitherBlocked(...)`, preserving the bounded repository ID query while enforcing the same block policy for all repository implementations.

Command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowApplicationServiceTest
```

Result summary:

- `BUILD SUCCESS`
- `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`

### Repository-level RED/GREEN refinement

The first fix was correct but would have added avoidable per-followee block checks on the DB path where `FollowMapper.listFolloweeIds(...)` already filters blocked relations in SQL. I tightened the implementation into the repository's existing `*ExcludingBlocked` contract pattern.

RED command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowApplicationServiceTest
```

Result summary:

- `BUILD FAILURE`
- Test compilation failed because `FollowRepository.listFolloweeIdsExcludingBlocked(...)` did not exist.

Final change made:

- Added default `FollowRepository.listFolloweeIdsExcludingBlocked(...)` backed by `listFolloweesExcludingBlocked(...)`, so Redis and in-memory repositories get block-aware ID results through the existing scan/filter path.
- Overrode `MyBatisFollowRepository.listFolloweeIdsExcludingBlocked(...)` to reuse the existing filtered SQL ID query and avoid extra block lookups.
- Updated `FollowApplicationService.listFolloweeIds(...)` to call the repository-level filtered ID contract.

GREEN command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowApplicationServiceTest
```

Result summary:

- `BUILD SUCCESS`
- `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`

### Remaining concerns

- None from the reported Redis parity issue.

### Post-fix Task Verification

Command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest,RedisFollowFeedCacheTest,FeedControllerTest,FollowApplicationServiceTest,SocialFollowQueryApiAdapterTest
```

Result summary:

- `BUILD SUCCESS`
- `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0`

## Review Fix Pass: Cache Freshness, Empty-Page Hits, And MVC Endpoint Coverage

### Review items addressed

1. Added bounded freshness to `RedisFollowFeedCache` by writing follow-feed pages with a local Redis TTL.
2. Fixed the cache contract so a stored empty page is treated as a cache hit rather than a miss.
3. Replaced the direct controller-method-only follow-feed assertion with a real `MockMvc` test for `GET /api/feed/follow`, covering request mapping, query params, auth extraction, and `rankVersion` in the response.

### Root cause summary

- `RedisFollowFeedCache.getOrLoadPage(...)` used `List.of()` for both cache miss and cached-empty payloads, so `FollowFeedReadApplicationService` would always rerun the loader for empty pages.
- `RedisFollowFeedCache` persisted pages with `ValueOperations.set(key, value)` and no TTL, so stale pages could survive indefinitely.
- `FeedControllerTest` already exercised `FeedController.follow(...)`, but only via a direct method call. That missed the actual Spring MVC mapping and request binding path.

### RED

Focused command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=RedisFollowFeedCacheTest,FeedControllerTest
```

Result summary:

- `BUILD FAILURE`
- `Tests run: 6, Failures: 2, Errors: 0, Skipped: 0`
- Failures:
  - `RedisFollowFeedCacheTest.getOrLoadPageShouldCacheLoadedIdsInRedisWithTtl`
    - expected Redis write via `set(key, value, Duration)` but implementation called `set(key, value)`
  - `RedisFollowFeedCacheTest.getOrLoadPageShouldTreatCachedEmptyPageAsCacheHit`
    - expected cached empty result, but loader ran and returned a non-empty page
- `FeedControllerTest` passed with the new real `MockMvc` path once added; no production controller change was required for that item.

### GREEN

Focused command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=RedisFollowFeedCacheTest,FeedControllerTest
```

Result summary:

- `BUILD SUCCESS`
- `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`

### Full Task 3 verification after fixes

Command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest,RedisFollowFeedCacheTest,FeedControllerTest,FollowApplicationServiceTest,SocialFollowQueryApiAdapterTest
```

Result summary:

- `BUILD SUCCESS`
- `Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`

### Files changed in this fix pass

- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisFollowFeedCache.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisFollowFeedCacheTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/controller/FeedControllerTest.java`

### Remaining concerns

- None from the reported review items.

## Re-review Fix Pass: Non-Redis FollowFeedCache Fallback And Explicit Redis Filtered-ID Override

### Review items addressed

1. Added a non-Redis `FollowFeedCache` fallback bean so `FollowFeedReadApplicationService` still has a cache dependency satisfied when `content.storage` is set to non-Redis modes such as `db`.
2. Added an explicit `RedisFollowRepository.listFolloweeIdsExcludingBlocked(...)` override that uses Redis’ existing bounded `listFiltered(...)` path and maps to IDs.

### Root cause summary

- `RedisFollowFeedCache` was the only production `FollowFeedCache` bean and it was conditional on `content.storage=redis`, so non-Redis content storage modes had no `FollowFeedCache` bean at all.
- Redis already had bounded filtered relation traversal through `listFolloweesExcludingBlocked(...)`, but the filtered ID path was still implicit via the interface default. The re-review asked for that contract to be explicit in the Redis implementation and visible in tests.

### RED

Focused command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=PassthroughFollowFeedCacheTest,RedisFollowRepositoryTest
```

Result summary:

- `BUILD FAILURE`
- Initial RED failed in test compilation because `PassthroughFollowFeedCache` did not exist.
- After adding the production types, the same focused command exposed one test harness issue in the new fallback test and confirmed the Redis explicit override path was ready to validate.
- Final corrected RED signal for the production gap remained the missing non-Redis fallback implementation.

### GREEN

Focused command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=PassthroughFollowFeedCacheTest,RedisFollowRepositoryTest
```

Result summary:

- `BUILD SUCCESS`
- `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`

### Full Task 3 verification after fixes

Command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest,RedisFollowFeedCacheTest,FeedControllerTest,FollowApplicationServiceTest,SocialFollowQueryApiAdapterTest,RedisFollowRepositoryTest,PassthroughFollowFeedCacheTest
```

Result summary:

- `BUILD SUCCESS`
- `Tests run: 27, Failures: 0, Errors: 0, Skipped: 0`

### Files changed in this fix pass

- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/PassthroughFollowFeedCache.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/RedisFollowRepository.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/PassthroughFollowFeedCacheTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/persistence/RedisFollowRepositoryTest.java`

### Remaining concerns

- None from the reported re-review items.

## Re-review Fix Pass: Follow-Feed Summary Enrichment Parity And ID-Based Pagination

### Review items addressed

1. Follow feed now uses the same summary enrichment path as hot-feed reads, including `PostSummaryCache`, latest activity, tags, and content-block preview assembly.
2. Follow feed pagination now derives `nextCursor` from the cached/loaded ID page shape rather than the rendered item count, so missing/deleted posts do not prematurely terminate pagination.

### Root cause summary

- `FollowFeedReadApplicationService` assembled summaries directly with `postSummaryAssembler.assemble(post, null, List.of(), "")`, which bypassed the existing application-layer summary loading path and produced thinner summaries than global/board feeds.
- `FollowFeedReadApplicationService` based `nextCursor` on `items.size()`, so a stale cached ID set that rendered fewer posts than requested could incorrectly suppress the next page even when later IDs existed.

### RED

Focused command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest,FeedReadApplicationServiceTest
```

Result summary:

- `BUILD FAILURE`
- `Tests run: 13, Failures: 2, Errors: 0, Skipped: 0`
- Failing regressions:
  - `listFollowFeedShouldUseEnrichedSummariesInsteadOfBareAssembly`
  - `listFollowFeedShouldEmitNextCursorWhenIdPageHasExtraIdEvenIfRenderedItemsShrink`
- `FeedReadApplicationServiceTest` stayed green, which confirmed the problem was isolated to the follow-feed read path.

### GREEN

Focused command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest,FeedReadApplicationServiceTest
```

Result summary:

- `BUILD SUCCESS`
- `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`

### Full Task 3 verification after fixes

Command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest,FeedReadApplicationServiceTest,RedisFollowFeedCacheTest,FeedControllerTest,FollowApplicationServiceTest,SocialFollowQueryApiAdapterTest,RedisFollowRepositoryTest,PassthroughFollowFeedCacheTest
```

Result summary:

- `BUILD SUCCESS`
- `Tests run: 39, Failures: 0, Errors: 0, Skipped: 0`

### Files changed in this fix pass

- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostFeedSummaryLoader.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedReadApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/FollowFeedReadApplicationService.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/application/FollowFeedReadApplicationServiceTest.java`

### Remaining concerns

- None from the reported follow-feed read-path issues.

## Re-review Fix Pass: Deep Follow-Feed Candidate Window Limit

### Review item addressed

1. Removed the repository-side hidden `500` clamp from `MyBatisPostContentRepository.listRecentVisiblePostsByAuthorIds(...)` so deep follow-feed pages can request a candidate window larger than 500 and avoid premature end-of-feed truncation.

### Root cause summary

- `FollowFeedReadApplicationService` intentionally computes a widening candidate window for deeper pages, but `MyBatisPostContentRepository.listRecentVisiblePostsByAuthorIds(...)` rewrote any caller-requested limit above `500` down to `500`.
- Because follow-feed pagination re-queries from the top candidate set and skips within that window, the clamp could truncate legitimate pages for active users before the application ever had a chance to merge and page the full candidate set.

### RED

Focused command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest,MyBatisPostContentRepositoryTest
```

Result summary:

- `BUILD FAILURE`
- `Tests run: 5, Failures: 2, Errors: 0, Skipped: 0`
- Failing signals:
  - `MyBatisPostContentRepositoryTest.listRecentVisiblePostsByAuthorIdsShouldForwardCallerLimitAboveFiveHundred`
    - expected mapper limit `641`, actual forwarded limit `500`
  - `FollowFeedReadApplicationServiceTest.listFollowFeedShouldRequestDeepCandidateWindowAboveFiveHundredWhenPageRequiresIt`
    - deep-page follow-feed path requested a fetch window above `500`, confirming the application behavior that the repository was truncating

### GREEN

Focused command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest,MyBatisPostContentRepositoryTest
```

Result summary:

- `BUILD SUCCESS`
- `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`

### Full Task 3 verification after fixes

Command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest,FeedReadApplicationServiceTest,MyBatisPostContentRepositoryTest,RedisFollowFeedCacheTest,FeedControllerTest,FollowApplicationServiceTest,SocialFollowQueryApiAdapterTest,RedisFollowRepositoryTest,PassthroughFollowFeedCacheTest
```

Result summary:

- `BUILD SUCCESS`
- `Tests run: 41, Failures: 0, Errors: 0, Skipped: 0`

### Files changed in this fix pass

- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostContentRepository.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/application/FollowFeedReadApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostContentRepositoryTest.java`

### Remaining concerns

- None from the reported deep-page follow-feed limit issue.

## Re-review Fix Pass: Bounded Seek Pagination For Follow Feed

### Review item addressed

1. Replaced follow-feed page-window pagination with bounded anchor pagination so deep follow-feed reads no longer re-query from the top or hard-stop after a few pages.

### Root cause summary

- `FollowFeedReadApplicationService` still used page-number math over a growing candidate window, so even after removing the repository-side `500` clamp, deep pages either amplified the query window or hit the later `MAX_CANDIDATE_WINDOW` guard and terminated early.
- The follow-feed cache only stored page-number keyed ID lists, so it had no way to preserve a stable seek anchor across requests.

### RED

Focused command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest,MyBatisPostContentRepositoryTest,RedisFollowFeedCacheTest,PassthroughFollowFeedCacheTest
```

Result summary:

- `BUILD FAILURE`
- Failure mode: compile-time red as expected while the new anchor contract did not exist yet
- Key missing pieces surfaced by the compiler:
  - `FollowFeedCache.FollowFeedPageSlice`
  - cursor-keyed `FollowFeedCache.getOrLoadPage(...)`
  - `PostContentRepository.listRecentVisiblePostsByAuthorIdsBefore(...)`
  - `DiscussPostMapper.selectRecentVisiblePostsByAuthorIdsBefore(...)`

### GREEN

Focused command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest,MyBatisPostContentRepositoryTest,RedisFollowFeedCacheTest,PassthroughFollowFeedCacheTest
```

Result summary:

- `BUILD SUCCESS`
- `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`

### Full Task 3 verification after fixes

Command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest,FeedReadApplicationServiceTest,MyBatisPostContentRepositoryTest,RedisFollowFeedCacheTest,FeedControllerTest,FollowApplicationServiceTest,SocialFollowQueryApiAdapterTest,RedisFollowRepositoryTest,PassthroughFollowFeedCacheTest
```

Result summary:

- `BUILD SUCCESS`
- `Tests run: 42, Failures: 0, Errors: 0, Skipped: 0`

### Files changed in this fix pass

- `backend/community-app/src/main/java/com/nowcoder/community/content/application/FollowFeedCache.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/FollowFeedCursorCodec.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/FollowFeedReadApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostContentRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostContentRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/PassthroughFollowFeedCache.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisFollowFeedCache.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/DiscussPostMapper.java`
- `backend/community-app/src/main/resources/mapper/discusspost-mapper.xml`
- `backend/community-app/src/test/java/com/nowcoder/community/content/application/FollowFeedReadApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostContentRepositoryTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/PassthroughFollowFeedCacheTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisFollowFeedCacheTest.java`

### Remaining concerns

- None from the reported bounded seek pagination issue.

## Re-review Fix Pass: Follow-Feed Anchor Tie-Break Order

### Review item addressed

1. Removed the application-side candidate re-sort so follow-feed seek pagination preserves the repository/SQL order used by the anchor predicate (`create_time desc, id desc`).

### Root cause summary

- MyBatis orders follow-feed candidates by `create_time desc, id desc` and uses the same tuple as the seek boundary.
- `FollowFeedReadApplicationService` re-sorted candidates in Java by `Date` and `UUID` natural order before choosing the anchor. Java UUID comparison is not guaranteed to match the database `BINARY(16)` ordering, so equal-timestamp pages could skip or repeat items.

### RED

Focused command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest
```

Result summary:

- `BUILD FAILURE`
- `Tests run: 5, Failures: 1, Errors: 0, Skipped: 0`
- Failing regression:
  - `listFollowFeedShouldPreserveRepositoryOrderForEqualTimestampAnchor`
    - expected repository order for equal-timestamp candidates, but the service re-sorted them before rendering and anchoring.

### GREEN

Focused command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest
```

Result summary:

- `BUILD SUCCESS`
- `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`

### Files changed in this fix pass

- `backend/community-app/src/main/java/com/nowcoder/community/content/application/FollowFeedReadApplicationService.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/application/FollowFeedReadApplicationServiceTest.java`

### Remaining concerns

- None from the reported anchor tie-break issue.

## Re-review Fix Pass: Excessive Follow-Feed Cursor Guard

### Review item addressed

1. Added a hard follow-feed candidate-window guard so a user-controlled cursor page cannot inflate the pull-merge candidate query without bound.

### Root cause summary

- `FeedCursorCodec` decodes any non-negative page number from the cursor.
- `FollowFeedReadApplicationService` used that page number to compute `(page + 1) * requestedSize * 4 + 1`, so a crafted deep cursor could force a very large repository query and in-memory sort/skip path.

### RED

Focused command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest
```

Result summary:

- `BUILD FAILURE`
- `Tests run: 5, Failures: 1, Errors: 0, Skipped: 0`
- Failing regression:
  - `listFollowFeedShouldStopBeforeLoadingWhenCursorRequiresTooLargeCandidateWindow`
    - expected no cache/repository/follow-query interaction for an excessive cursor, but the service still called `followFeedCache.getOrLoadPage(...)`.

### GREEN

Focused command run:

```bash
cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest
```

Result summary:

- `BUILD SUCCESS`
- `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`

### Files changed in this fix pass

- `backend/community-app/src/main/java/com/nowcoder/community/content/application/FollowFeedReadApplicationService.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/application/FollowFeedReadApplicationServiceTest.java`

### Remaining concerns

- None from the reported excessive cursor issue.
