# BBS Reliability P3 Hotpath Optimization Design

## Context

This design covers the P3 high-concurrency optimization wave for the BBS reliability platform. The current `origin/main` already contains the P0/P1/P2 reliability foundation:

- hot feed read metrics through `community_cache_requests_total{cache="hot_feed",result,scope}`
- hot feed fallback from Redis cache to owner repository
- cold-cache best-effort feed warming inside `FeedReadApplicationService`
- Redis-backed hot feed projection guard for source event/version ordering
- cache payload cleanup for post summary/detail and comment/follow page JSON payloads
- post counter cache, owner-current overlay on detail reads, and dirty counter snapshot flush job
- generic Redis `SingleFlightTaskGuard` for scheduled work
- gateway-first k6 load test suite

P3 must build on those capabilities. It must not introduce P1/P2 ops governance APIs, make hot feed a new owner, or bypass DDD tactical layering.

## Goal

Implement an independent P3-A PR that hardens the BBS hot read path against cache stampede, cache avalanche, poison payloads, and unmeasured capacity risk while keeping all business orchestration inside the `content` domain.

## Non-Goals

- Do not add `/api/ops/**` endpoints or governance workflows.
- Do not change outbox replay, projection lag, or admin security behavior.
- Do not create a new cache framework outside the existing content cache/projection model.
- Do not implement full rank-version dual-key migration in P3-A.
- Do not implement full counter reconciliation/repair state machines in P3-A.
- Do not make Redis cache data a business fact source.

## Scope Split

### P3-A: This PR

P3-A implements:

- hot key prewarm for global and board hot feed pages, plus selected detail shells
- TTL jitter for hot path payload caches
- single-flight protection around hot feed/detail repository fallback
- hot feed/counter/rank poison cleanup for invalid Redis payloads or members
- hot-path-specific k6 scenario and capacity acceptance documentation

### P3-B: Later Independent PR

P3-B should implement:

- versioned rank key dual-read/dual-write switching and old-version cleanup
- counter consistency repair/reconcile job with drift detection, metrics, and single-flight

P3-A can prepare small extension points for these later tasks only when they are needed for the P3-A implementation. It must not implement partial P3-B behavior hidden behind documentation.

## Architecture

The implementation stays within existing DDD boundaries:

```text
content.infrastructure.job
  -> content.application.*ApplicationService
      -> content domain repositories / application cache ports
      -> foreign owner api.query only when existing content application services already use them
          -> content.infrastructure cache/repository implementations
```

Allowed P3-A additions:

- `content.application` use-case services and application-owned cache ports.
- `content.infrastructure.persistence` Redis implementations of content cache ports.
- `content.infrastructure.job` scheduled prewarm or cleanup jobs that call same-domain content application services only.
- `content.infrastructure.observability` or existing application metrics helpers with bounded labels.
- `tests/k6` scenario files.

Forbidden P3-A additions:

- controller-to-infrastructure, job-to-repository, job-to-cache, or job-to-foreign-api shortcuts.
- new root legacy `service`, `entity`, `mapper`, or `app` usage.
- metric labels containing post IDs, board IDs, Redis keys, trace IDs, or raw exception messages.

## Existing Main Completion Inventory

| P3 capability | Existing main state | P3-A decision |
| --- | --- | --- |
| Hot key prewarm | Cold miss fallback warms the requested feed page. No standalone warmup job. | Add explicit prewarm application service and scheduled job. |
| TTL jitter | Hot feed, summary, detail, and counter caches do not have jittered TTLs. Comment/follow caches use fixed TTLs. | Add bounded jitter helper and use it in hot path payload cache writes. |
| Single-flight origin protection | Generic `SingleFlightTaskGuard` exists, but hot feed/detail fallback does not use per-key protection. | Add content application-owned single-flight port and Redis adapter. |
| Payload poison cleanup | Summary/detail/comment/follow JSON poison cleanup exists. Hot feed ZSET member cleanup and counter/rank invalid payload cleanup are incomplete. | Clean invalid hot feed members and invalid rank/counter payloads where read paths can safely identify them. |
| Rank version switching | Current rank version string exists; projection guard prevents source version regression. No dual versioned rank keys. | Do not implement full switch in P3-A; preserve current behavior and document P3-B handoff. |
| Counter consistency repair | Counter overlay and dirty snapshot flush exist. No repair job. | Do not implement full repair in P3-A; avoid making counter drift worse and add capacity observation to hot-path tests. |
| Load/capacity acceptance | Generic k6 suite exists, but no hot feed/detail cache scenario. | Add hot-path k6 scenario and handbook acceptance gates. |

## P3-A Design

### Hot Key Prewarm

Add `HotPathPrewarmApplicationService` in `content.application`. It should:

- warm global hot feed pages from `PostContentRepository.listPosts(... ORDER_HOT)`
- warm configured board hot feed pages from existing categories
- warm detail shells for the same post IDs through existing read-model assembly paths
- write feed rank version through `PostFeedCache.writeRankVersion(...)`
- write feed members through existing `PostFeedCache.upsertGlobalHot(...)` / `upsertBoardHot(...)`
- write summaries through existing `PostSummaryCache.putAll(...)`
- avoid direct controller or job access to repositories or cache infrastructure

`HotPathPrewarmJob` in `content.infrastructure.job` calls only `HotPathPrewarmApplicationService`. The job is guarded by a single-flight lock so multiple app instances do not prewarm the same hot set concurrently.

Benefit:

- reduces cold-start DB bursts after deploy, Redis restart, or rank cache eviction
- keeps first-page hot feed latency predictable under traffic spikes

Risk:

- prewarm can add background DB load
- warming stale rankings can temporarily bias hot feed toward old DB scores
- overly broad board prewarm can waste Redis memory

Observation:

- record `community_cache_requests_total{cache="hot_feed",result="prewarm",scope}`
- record `community_job_runs_total{job.name="content-hot-path-prewarm",result}`
- use existing Redis slow-command and DB slow-query runtime logs

Testing:

- application unit test verifies global and board prewarm writes feed, summary, detail, and rank version through ports
- job unit test verifies disabled job does nothing and enabled job uses same-domain application service
- single-flight job test verifies second concurrent instance skips

### TTL Jitter

Add a small content application value object, `CacheTtlPolicy`, and a Redis-facing helper that derives bounded TTLs:

```text
effective_ttl = base_ttl + stable_jitter(key, max_jitter)
```

The jitter must be deterministic per cache key within a process-independent hash so repeated writes for the same key do not churn expiry every call. It must be bounded and never return zero or negative TTL.

Apply jitter to payload caches that can safely expire and rebuild:

- `RedisPostSummaryCache`
- `RedisPostDetailCache`
- `RedisCommentPageCache`
- `RedisFollowFeedCache`

Do not apply TTL to counters or durable projection guards in P3-A unless the current data model already treats the value as expirable.

Benefit:

- avoids synchronized expiration avalanche across hot summary/detail/comment/follow keys

Risk:

- too-short TTL increases repository fallback
- too-long TTL increases stale read-model lifetime
- adding TTL to a cache that previously persisted forever can surface more misses

Observation:

- no high-cardinality TTL metrics
- use cache request result metrics, Redis command latency, DB query latency, and k6 thresholds

Testing:

- unit test verifies deterministic bounded TTL per key
- Redis cache tests verify writes use TTL and delete poison payloads
- no tests should assert exact random values; assert ranges

### Single-Flight Origin Protection

Add an application-owned `HotPathSingleFlight` port with:

```java
<T> T execute(String scope, String key, Duration ttl, Supplier<T> loader, Supplier<T> fallbackWhenBusy)
```

The content application service uses this port around repository fallback for:

- global hot feed page fallback
- board hot feed page fallback
- post detail shell cache miss fallback

Redis implementation can reuse the same SET NX + token + compare-and-delete pattern as `SingleFlightTaskGuard`, but exposed through a content application port. If the lock is busy, the caller should avoid stampeding the repository and return a safe empty/degraded result or a cached stale value when available. P3-A should keep behavior conservative: feed fallback can return empty/degraded when busy; detail fallback can run without single-flight only if no safe response exists.

Benefit:

- caps DB QPS during hot feed/detail cache breakdown
- protects first-page feed and popular post detail endpoints

Risk:

- lock contention can increase empty/degraded responses
- Redis outage can either skip protection or force degraded response; the policy must be explicit per path

Observation:

- record bounded outcomes for `community_cache_requests_total`, including `singleflight_busy` and `singleflight_error`
- inspect DB pool pressure and Redis slow operations during k6 hot-path runs

Testing:

- application tests verify one loader call when multiple requests hit the same feed key
- busy-lock tests verify repository fallback is skipped for feed paths
- Redis adapter tests verify lock release uses token compare

### Payload Poison Cleanup

Extend cleanup beyond JSON payload caches:

- `RedisPostFeedCache.readIds(...)` deletes invalid UUID members from the ZSET it read.
- `RedisPostFeedCache.readRankVersion()` deletes blank or unsupported rank version payloads and returns configured fallback.
- `RedisPostCounterCache.get(...)` removes or ignores invalid numeric fields and marks the counter dirty only when it can do so without inventing facts.

Benefit:

- prevents one corrupted Redis value or member from causing repeated parse failures
- makes cache rebuild paths self-healing

Risk:

- overly aggressive cleanup can delete useful data if validation rules are too strict
- counter cleanup must not convert an unknown state into a false business fact

Observation:

- record bounded cleanup counts with `community_cache_requests_total{cache,result="poison_cleanup",scope}`
- keep logs sanitized; never log raw Redis keys or payloads

Testing:

- Redis cache unit tests verify invalid ZSET member removal
- rank version tests verify invalid value cleanup and fallback
- counter tests verify invalid numeric fields do not crash reads

### Rank Version Switching

P3-A preserves the current rank version contract:

- feed responses continue to include `rankVersion`
- projection still writes `content.feed.hot-rank-version`
- `HotFeedProjectionGuard` still rejects duplicate and stale source versions

P3-B should add dual-version rank keys with explicit active/previous version read rules.

Benefit:

- the P3-A PR does not destabilize ranking semantics while adding cache protection

Risk:

- current rank version still behaves as a label rather than a full switch

Observation:

- keep current response `rankVersion`
- P3-B must add version-switch-specific metrics and cleanup signals

Testing:

- P3-A keeps existing rank version round-trip and stale projection regression tests passing
- P3-B will add old-cursor/new-version switch tests

### Counter Consistency Repair

P3-A preserves current counter behavior:

- detail reads overlay owner-current like/comment/score values through `PostCounterApplicationService.read(...)`
- dirty counter snapshot flush continues to persist cached counter snapshots

P3-A does not implement drift scanning or repair commands. It must avoid direct mutation of counters outside the existing content application service.

Benefit:

- avoids mixing cache protection with a repair state machine

Risk:

- existing view/bookmark counter drift remains possible until P3-B

Observation:

- use detail response correctness tests and hot-path k6 latency/error metrics
- P3-B should add drift count and repair result metrics

Testing:

- keep existing counter overlay and flush tests passing
- P3-B will add drift detection and repair idempotency tests

### Load And Capacity Acceptance

Add `tests/k6/scenarios/hot-path.js` and npm script support. The scenario should:

- call `/api/feed/global?size=20`
- call `/api/boards/{boardId}/feed?size=20` when `K6_BOARD_ID` is provided
- call `/api/posts/{postId}` when `K6_POST_ID` is provided
- tag requests with bounded `type:api` and hot-path-specific tags
- use thresholds for p95, p99, failure rate, and unexpected status rate

Capacity acceptance for local cluster:

- hot feed/detail HTTP error rate `< 1%`
- hot path p95 `< 800ms`, p99 `< 1500ms`
- no sustained DB pool pending pressure during cache-warm runs
- Redis read/write failures produce degraded/fallback metrics instead of request failure spikes
- repeated cold runs show lower repository fallback after prewarm

Benefit:

- makes P3 optimizations objectively testable

Risk:

- local thresholds are engineering baselines, not production SLOs
- seed data may not include enough hot posts/boards unless documented

Observation:

- k6 summary
- `/actuator/prometheus`
- Kibana runtime logs for access, database, cache, and job categories

Testing:

- k6 smoke for the new scenario against local gateway
- static test for script import/build through the existing k6 npm test path

## Configuration

New properties should stay under content-owned prefixes:

```yaml
content:
  hot-path:
    prewarm:
      enabled: true
      delay-ms: 60000
      pages: 2
      page-size: 20
      board-limit: 20
      lock-ttl-seconds: 30
    single-flight:
      enabled: true
      ttl-ms: 3000
    cache:
      summary-ttl-seconds: 300
      detail-ttl-seconds: 300
      comment-page-ttl-seconds: 120
      follow-page-ttl-seconds: 60
      ttl-jitter-seconds: 60
```

Exact names may be adjusted during implementation if existing property naming makes a different spelling clearer, but they must remain content-owned and documented.

## Metrics

Use only bounded dimensions already allowed by `docs/handbook/observability.md`.

Extend `community_cache_requests_total{cache,result,scope}` with bounded values:

- `cache="hot_feed"`, `scope="global" | "board" | "rank_version" | "prewarm"`
- `result="hit" | "fallback" | "empty" | "degraded" | "singleflight_busy" | "singleflight_error" | "poison_cleanup" | "prewarm"`
- `cache="post_detail"`, `scope="detail"`
- `cache="post_summary"`, `scope="summary"`

Do not use post IDs, board IDs, Redis keys, event IDs, trace IDs, or exception class names as metric labels.

## Testing Strategy

Run focused tests after implementation:

```bash
cd backend
mvn test -pl :community-app -Dtest='FeedReadApplicationServiceReliabilityTest,PostReadApplicationServiceTest,RedisPostFeedCacheTest,RedisPostSummaryCacheTest,RedisPostDetailCacheTest,RedisPostCounterCacheTest,HotPathPrewarmApplicationServiceTest,HotPathPrewarmJobTest'
```

Run architecture tests if any package boundary changes:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Run k6 validation after a local stack is up:

```bash
cd tests/k6
npm test
npm run hot-path
```

## Documentation Updates

Update:

- `docs/handbook/reliability.md` for hot path cache protection semantics
- `docs/handbook/operations.md` for hot path prewarm and cache degradation runbook
- `docs/handbook/observability.md` for any new bounded metric result values
- `docs/handbook/performance-testing.md` and `tests/k6/README.md` for the new hot-path scenario

## Approval Gate

After this spec and its implementation plan are reviewed, implementation can begin. Until then, no backend or k6 code should be changed.
