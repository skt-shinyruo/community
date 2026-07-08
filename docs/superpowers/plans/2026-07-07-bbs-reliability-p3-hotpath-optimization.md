# BBS Reliability P3 Hotpath Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the P3-A hot-path optimization PR for BBS reliability: hot key prewarm, TTL jitter, single-flight origin protection, payload poison cleanup, and hot-path capacity acceptance.

**Architecture:** Keep all orchestration in the `content` domain. Controllers and jobs call same-domain `*ApplicationService`; application services use content application ports and domain repositories; Redis details stay in `content.infrastructure.persistence`.

**Tech Stack:** Java 17, Spring Boot configuration properties, Spring scheduling, Redis `StringRedisTemplate`, Micrometer, JUnit 5, Mockito, Maven, k6, npm.

## Global Constraints

- `content` remains the owner for posts, comments, hot-feed source facts, and content read-model orchestration.
- `social` remains the owner for likes, follows, and blocks.
- Hot feed, summaries, detail shells, and counters remain derived read models or caches, not business facts.
- Controllers, listeners, handlers, bridges, enqueuers, and jobs call only same-domain `*ApplicationService`.
- Cross-domain synchronous collaboration uses foreign owner `api.query` / `api.action` / `api.model`.
- Cross-domain asynchronous collaboration uses owner `contracts.event`.
- P3-A must not add `/api/ops/**` endpoints, outbox governance workflows, projection replay changes, or P1/P2 admin APIs.
- P3-A must not implement full dual-rank-version switching or counter repair state machines; those remain P3-B scope.
- Metric labels must stay bounded and must not contain post IDs, board IDs, Redis keys, trace IDs, exception messages, or raw payload values.
- Plans and specs live under `docs/superpowers`; handbook docs live under `docs/handbook`.

---

## Scope Check

This plan implements only P3-A from `docs/superpowers/specs/2026-07-07-bbs-reliability-p3-hotpath-optimization-design.md`.

It intentionally does not implement P3-B dual rank-version switching or full counter repair. Existing rank source-version guard, rank label response, counter overlay, and dirty snapshot flush must keep passing their current tests.

## File Structure

### Content Application Configuration And Metrics

- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentHotPathProperties.java`
  Configuration properties for hot-path prewarm, single-flight, and cache TTLs.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/CacheTtlPolicy.java`
  Deterministic bounded TTL jitter helper.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/HotFeedReadMetrics.java`
  Keep existing `record(result, scope)` and add bounded generic cache result recording for P3-A hot path outcomes.
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CacheTtlPolicyTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/HotFeedReadMetricsTest.java`

### Redis Cache Hardening

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostSummaryCache.java`
  Write summary payloads with jittered TTL and preserve poison cleanup.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostDetailCache.java`
  Write detail payloads with jittered TTL and preserve poison cleanup.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisCommentPageCache.java`
  Apply TTL jitter to page and index keys.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisFollowFeedCache.java`
  Apply TTL jitter to follow feed page keys.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCache.java`
  Delete invalid UUID members found while reading hot feed ZSET pages and reject blank rank version payloads.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostCounterCache.java`
  Ignore invalid numeric counter fields and delete only fields that are known invalid cache payloads.
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostSummaryCacheTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostDetailCacheTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisCommentPageCacheTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisFollowFeedCacheTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCacheTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostCounterCacheTest.java`

### Content Single-Flight

- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/HotPathSingleFlight.java`
  Application-owned port for per-key hot path origin protection.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisHotPathSingleFlight.java`
  Redis implementation using token lock and compare-and-delete release.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/NoopHotPathSingleFlight.java`
  Fallback implementation when Redis single-flight is disabled or unavailable.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedReadApplicationService.java`
  Protect repository fallback for global and board hot feed pages.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostReadApplicationService.java`
  Protect detail shell load on cache miss.
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisHotPathSingleFlightTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/FeedReadApplicationServiceReliabilityTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostReadApplicationServiceTest.java`

### Hot Key Prewarm

- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/HotPathPrewarmApplicationService.java`
  Same-domain application service for global feed, board feed, summary, and detail-shell prewarm.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/HotPathPrewarmResult.java`
  Bounded result counts for job logging and tests.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/job/HotPathPrewarmJob.java`
  Scheduled inbound adapter that calls only `HotPathPrewarmApplicationService`.
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/HotPathPrewarmApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/job/HotPathPrewarmJobTest.java`

### Capacity Acceptance And Docs

- Create: `tests/k6/scenarios/hot-path.js`
  Gateway-first hot feed/detail load scenario.
- Modify: `tests/k6/package.json`
  Add `hot-path` script.
- Modify: `tests/k6/README.md`
  Document scenario inputs and thresholds.
- Modify: `docs/handbook/performance-testing.md`
  Add hot-path capacity workflow and local acceptance gates.
- Modify: `docs/handbook/reliability.md`
  Add P3-A cache protection semantics.
- Modify: `docs/handbook/operations.md`
  Add hot-path prewarm and cache degradation runbook.
- Modify: `docs/handbook/observability.md`
  Document new bounded cache metric result values.

---

### Task 1: Content Hot-Path Configuration, TTL Policy, And Metrics

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentHotPathProperties.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/CacheTtlPolicy.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/HotFeedReadMetrics.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CacheTtlPolicyTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/HotFeedReadMetricsTest.java`

**Interfaces:**
- Produces: `ContentHotPathProperties.CacheProperties.summaryTtl()`, `detailTtl()`, `commentPageTtl()`, `followPageTtl()`, `ttlJitter()`.
- Produces: `CacheTtlPolicy.jitteredTtl(String cacheKey, Duration baseTtl)`.
- Produces: `HotFeedReadMetrics.recordCache(String cache, String result, String scope)`.
- Keeps: `HotFeedReadMetrics.record(String result, String scope)`.

- [ ] **Step 1: Write TTL policy tests**

Create `backend/community-app/src/test/java/com/nowcoder/community/content/application/CacheTtlPolicyTest.java`:

```java
package com.nowcoder.community.content.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CacheTtlPolicyTest {

    @Test
    void jitteredTtlShouldStayWithinConfiguredRange() {
        ContentHotPathProperties properties = new ContentHotPathProperties();
        properties.getCache().setTtlJitterSeconds(60);
        CacheTtlPolicy policy = new CacheTtlPolicy(properties);

        Duration ttl = policy.jitteredTtl("post:summary:1", Duration.ofSeconds(300));

        assertThat(ttl).isGreaterThanOrEqualTo(Duration.ofSeconds(300));
        assertThat(ttl).isLessThanOrEqualTo(Duration.ofSeconds(360));
    }

    @Test
    void jitteredTtlShouldBeStableForSameKey() {
        ContentHotPathProperties properties = new ContentHotPathProperties();
        properties.getCache().setTtlJitterSeconds(60);
        CacheTtlPolicy policy = new CacheTtlPolicy(properties);

        Duration first = policy.jitteredTtl("post:detail:1", Duration.ofSeconds(120));
        Duration second = policy.jitteredTtl("post:detail:1", Duration.ofSeconds(120));

        assertThat(second).isEqualTo(first);
    }

    @Test
    void jitteredTtlShouldReturnPositiveTtlWhenBaseIsInvalid() {
        ContentHotPathProperties properties = new ContentHotPathProperties();
        properties.getCache().setTtlJitterSeconds(60);
        CacheTtlPolicy policy = new CacheTtlPolicy(properties);

        Duration ttl = policy.jitteredTtl("post:detail:1", Duration.ZERO);

        assertThat(ttl).isEqualTo(Duration.ofSeconds(1));
    }
}
```

- [ ] **Step 2: Write metric extension tests**

Create `backend/community-app/src/test/java/com/nowcoder/community/content/application/HotFeedReadMetricsTest.java`:

```java
package com.nowcoder.community.content.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HotFeedReadMetricsTest {

    @Test
    void recordCacheShouldUseBoundedLabels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HotFeedReadMetrics metrics = new HotFeedReadMetrics(registry);

        metrics.recordCache("post_detail", "singleflight_busy", "detail");

        Counter counter = registry.find("community_cache_requests_total")
                .tags("cache", "post_detail", "result", "singleflight_busy", "scope", "detail")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void existingHotFeedRecordShouldRemainCompatible() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HotFeedReadMetrics metrics = new HotFeedReadMetrics(registry);

        metrics.record("fallback", "global");

        Counter counter = registry.find("community_cache_requests_total")
                .tags("cache", "hot_feed", "result", "fallback", "scope", "global")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=CacheTtlPolicyTest,HotFeedReadMetricsTest
```

Expected: compilation fails because `ContentHotPathProperties`, `CacheTtlPolicy`, and `recordCache(...)` do not exist.

- [ ] **Step 4: Add content hot-path properties**

Create `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentHotPathProperties.java`:

```java
package com.nowcoder.community.content.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "content.hot-path")
public class ContentHotPathProperties {

    private final PrewarmProperties prewarm = new PrewarmProperties();
    private final SingleFlightProperties singleFlight = new SingleFlightProperties();
    private final CacheProperties cache = new CacheProperties();

    public PrewarmProperties getPrewarm() {
        return prewarm;
    }

    public SingleFlightProperties getSingleFlight() {
        return singleFlight;
    }

    public CacheProperties getCache() {
        return cache;
    }

    public static class PrewarmProperties {
        private boolean enabled = true;
        private long delayMs = 60_000L;
        private int pages = 2;
        private int pageSize = 20;
        private int boardLimit = 20;
        private long lockTtlSeconds = 30L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }

        public int getPages() {
            return pages;
        }

        public void setPages(int pages) {
            this.pages = pages;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public int getBoardLimit() {
            return boardLimit;
        }

        public void setBoardLimit(int boardLimit) {
            this.boardLimit = boardLimit;
        }

        public Duration lockTtl() {
            return Duration.ofSeconds(Math.max(1L, lockTtlSeconds));
        }

        public long getLockTtlSeconds() {
            return lockTtlSeconds;
        }

        public void setLockTtlSeconds(long lockTtlSeconds) {
            this.lockTtlSeconds = lockTtlSeconds;
        }
    }

    public static class SingleFlightProperties {
        private boolean enabled = true;
        private long ttlMs = 3_000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getTtlMs() {
            return ttlMs;
        }

        public void setTtlMs(long ttlMs) {
            this.ttlMs = ttlMs;
        }

        public Duration ttl() {
            return Duration.ofMillis(Math.max(1L, ttlMs));
        }
    }

    public static class CacheProperties {
        private long summaryTtlSeconds = 300L;
        private long detailTtlSeconds = 300L;
        private long commentPageTtlSeconds = 120L;
        private long followPageTtlSeconds = 60L;
        private long ttlJitterSeconds = 60L;

        public long getSummaryTtlSeconds() {
            return summaryTtlSeconds;
        }

        public void setSummaryTtlSeconds(long summaryTtlSeconds) {
            this.summaryTtlSeconds = summaryTtlSeconds;
        }

        public long getDetailTtlSeconds() {
            return detailTtlSeconds;
        }

        public void setDetailTtlSeconds(long detailTtlSeconds) {
            this.detailTtlSeconds = detailTtlSeconds;
        }

        public long getCommentPageTtlSeconds() {
            return commentPageTtlSeconds;
        }

        public void setCommentPageTtlSeconds(long commentPageTtlSeconds) {
            this.commentPageTtlSeconds = commentPageTtlSeconds;
        }

        public long getFollowPageTtlSeconds() {
            return followPageTtlSeconds;
        }

        public void setFollowPageTtlSeconds(long followPageTtlSeconds) {
            this.followPageTtlSeconds = followPageTtlSeconds;
        }

        public long getTtlJitterSeconds() {
            return ttlJitterSeconds;
        }

        public void setTtlJitterSeconds(long ttlJitterSeconds) {
            this.ttlJitterSeconds = ttlJitterSeconds;
        }

        public Duration summaryTtl() {
            return seconds(summaryTtlSeconds);
        }

        public Duration detailTtl() {
            return seconds(detailTtlSeconds);
        }

        public Duration commentPageTtl() {
            return seconds(commentPageTtlSeconds);
        }

        public Duration followPageTtl() {
            return seconds(followPageTtlSeconds);
        }

        public Duration ttlJitter() {
            return seconds(Math.max(0L, ttlJitterSeconds));
        }

        private static Duration seconds(long value) {
            return Duration.ofSeconds(Math.max(1L, value));
        }
    }
}
```

- [ ] **Step 5: Add TTL policy**

Create `backend/community-app/src/main/java/com/nowcoder/community/content/application/CacheTtlPolicy.java`:

```java
package com.nowcoder.community.content.application;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.CRC32;

@Component
public class CacheTtlPolicy {

    private final ContentHotPathProperties properties;

    public CacheTtlPolicy(ContentHotPathProperties properties) {
        this.properties = properties == null ? new ContentHotPathProperties() : properties;
    }

    public Duration jitteredTtl(String cacheKey, Duration baseTtl) {
        Duration safeBase = positive(baseTtl);
        Duration jitter = properties.getCache().ttlJitter();
        long jitterSeconds = jitter.toSeconds();
        if (jitterSeconds <= 0L || !StringUtils.hasText(cacheKey)) {
            return safeBase;
        }
        long offset = stablePositiveHash(cacheKey.trim()) % (jitterSeconds + 1L);
        return safeBase.plusSeconds(offset);
    }

    private static Duration positive(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            return Duration.ofSeconds(1L);
        }
        return value;
    }

    private static long stablePositiveHash(String value) {
        CRC32 crc32 = new CRC32();
        crc32.update(value.getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }
}
```

- [ ] **Step 6: Extend hot feed cache metrics**

Modify `backend/community-app/src/main/java/com/nowcoder/community/content/application/HotFeedReadMetrics.java` so it contains:

```java
package com.nowcoder.community.content.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HotFeedReadMetrics {

    private final MeterRegistry meterRegistry;

    @Autowired
    public HotFeedReadMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable());
    }

    HotFeedReadMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(String result, String scope) {
        recordCache("hot_feed", result, scope);
    }

    public void recordCache(String cache, String result, String scope) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                "community_cache_requests_total",
                Tags.of(
                        "cache", bounded(cache),
                        "result", bounded(result),
                        "scope", bounded(scope)
                )
        ).increment();
    }

    private String bounded(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 40 ? trimmed : trimmed.substring(0, 40);
    }
}
```

- [ ] **Step 7: Run task tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=CacheTtlPolicyTest,HotFeedReadMetricsTest,FeedReadApplicationServiceReliabilityTest
```

Expected: all selected tests pass.

- [ ] **Step 8: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentHotPathProperties.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/application/CacheTtlPolicy.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/application/HotFeedReadMetrics.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/application/CacheTtlPolicyTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/application/HotFeedReadMetricsTest.java
git commit -m "feat: add content hot path cache policy"
```

### Task 2: TTL Jitter And Cache Poison Cleanup

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostSummaryCache.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostDetailCache.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisCommentPageCache.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisFollowFeedCache.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCache.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostCounterCache.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostSummaryCacheTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostDetailCacheTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCacheTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostCounterCacheTest.java`

**Interfaces:**
- Consumes: `CacheTtlPolicy.jitteredTtl(...)`.
- Consumes: `ContentHotPathProperties.CacheProperties`.
- Produces: Redis cache writes with bounded TTL jitter.
- Produces: hot feed invalid UUID member cleanup.

- [ ] **Step 1: Add failing Redis cache tests**

Extend the existing Redis cache tests with focused assertions:

```java
@Test
void postDetailPutShouldUseJitteredTtl() {
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    JsonCodec jsonCodec = mock(JsonCodec.class);
    CacheTtlPolicy ttlPolicy = mock(CacheTtlPolicy.class);
    PostDetailResult detail = detail(uuid(1));
    when(jsonCodec.toJson(detail)).thenReturn("{\"id\":\"1\"}");
    when(ttlPolicy.jitteredTtl("post:detail:" + uuid(1), Duration.ofSeconds(300)))
            .thenReturn(Duration.ofSeconds(333));

    RedisPostDetailCache cache = new RedisPostDetailCache(redisTemplate, jsonCodec, ttlPolicy, hotPathProperties());

    cache.put(uuid(1), detail);

    verify(redisTemplate.opsForValue()).set("post:detail:" + uuid(1), "{\"id\":\"1\"}", Duration.ofSeconds(333));
}
```

Add equivalent tests for:

- `RedisPostSummaryCache.putAll(...)` using summary TTL
- `RedisPostFeedCache.readGlobalHotIds(...)` removing invalid UUID members from the ZSET key
- `RedisPostCounterCache.get(...)` deleting invalid numeric hash fields and returning safe zeros for those fields

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=RedisPostSummaryCacheTest,RedisPostDetailCacheTest,RedisPostFeedCacheTest,RedisPostCounterCacheTest
```

Expected: selected tests fail because constructors and TTL/cleanup behavior have not been implemented.

- [ ] **Step 3: Apply TTL policy to summary and detail caches**

Modify constructors and writes:

```java
private final CacheTtlPolicy ttlPolicy;
private final ContentHotPathProperties hotPathProperties;

public RedisPostDetailCache(
        StringRedisTemplate redisTemplate,
        JsonCodec jsonCodec,
        CacheTtlPolicy ttlPolicy,
        ContentHotPathProperties hotPathProperties
) {
    this.redisTemplate = redisTemplate;
    this.jsonCodec = jsonCodec;
    this.ttlPolicy = ttlPolicy == null ? new CacheTtlPolicy(new ContentHotPathProperties()) : ttlPolicy;
    this.hotPathProperties = hotPathProperties == null ? new ContentHotPathProperties() : hotPathProperties;
}

@Override
public void put(UUID postId, PostDetailResult detail) {
    if (postId == null || detail == null) {
        return;
    }
    String key = key(postId);
    redisTemplate.opsForValue().set(
            key,
            jsonCodec.toJson(detail),
            ttlPolicy.jitteredTtl(key, hotPathProperties.getCache().detailTtl())
    );
}
```

Apply the same pattern to `RedisPostSummaryCache.putAll(...)`, using `hotPathProperties.getCache().summaryTtl()`.

- [ ] **Step 4: Apply TTL jitter to comment and follow page caches**

Modify `RedisCommentPageCache.putRootPage(...)` and `RedisFollowFeedCache.getOrLoadPage(...)` so they compute TTL per key:

```java
Duration pageTtl = ttlPolicy.jitteredTtl(key, hotPathProperties.getCache().commentPageTtl());
redisTemplate.opsForValue().set(key, jsonCodec.toJson(result), pageTtl);
redisTemplate.opsForSet().add(indexKey, key);
redisTemplate.expire(indexKey, pageTtl);
```

For follow feed pages:

```java
Duration pageTtl = ttlPolicy.jitteredTtl(key, hotPathProperties.getCache().followPageTtl());
redisTemplate.opsForValue().set(key, jsonCodec.toJson(serialize(loaded)), pageTtl);
```

- [ ] **Step 5: Clean invalid hot feed ZSET members**

Modify `RedisPostFeedCache.readIds(...)`:

```java
List<String> poisonMembers = new ArrayList<>();
List<UUID> ids = new ArrayList<>();
for (String rawId : rawIds) {
    UUID parsed = parseUuid(rawId);
    if (parsed == null) {
        if (StringUtils.hasText(rawId)) {
            poisonMembers.add(rawId);
        }
        continue;
    }
    ids.add(parsed);
}
if (!poisonMembers.isEmpty()) {
    redisTemplate.opsForZSet().remove(key, poisonMembers.toArray(Object[]::new));
}
return ids;
```

Keep `readGlobalHotIds(...)` and `readBoardHotIds(...)` response shapes unchanged.

- [ ] **Step 6: Clean invalid counter payload fields conservatively**

Modify `RedisPostCounterCache.get(...)` to collect invalid numeric hash fields:

```java
List<Object> invalidFields = new ArrayList<>();
long viewCount = longValue(values.get(FIELD_VIEW), FIELD_VIEW, invalidFields);
long likeCount = longValue(values.get(FIELD_LIKE), FIELD_LIKE, invalidFields);
long commentCount = longValue(values.get(FIELD_COMMENT), FIELD_COMMENT, invalidFields);
long bookmarkCount = longValue(values.get(FIELD_BOOKMARK), FIELD_BOOKMARK, invalidFields);
double score = doubleValue(values.get(FIELD_SCORE), FIELD_SCORE, invalidFields);
if (!invalidFields.isEmpty()) {
    redisTemplate.opsForHash().delete(counterKey(postId), invalidFields.toArray());
}
return new PostCounterSnapshot(postId, viewCount, likeCount, commentCount, bookmarkCount, score);
```

Use helper methods that return zero only for invalid cache values:

```java
private static long longValue(Object raw, String field, List<Object> invalidFields) {
    if (raw == null) {
        return 0L;
    }
    try {
        return Long.parseLong(raw.toString());
    } catch (NumberFormatException ex) {
        invalidFields.add(field);
        return 0L;
    }
}
```

- [ ] **Step 7: Run task tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=RedisPostSummaryCacheTest,RedisPostDetailCacheTest,RedisCommentPageCacheTest,RedisFollowFeedCacheTest,RedisPostFeedCacheTest,RedisPostCounterCacheTest
```

Expected: all selected tests pass.

- [ ] **Step 8: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostSummaryCache.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostDetailCache.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisCommentPageCache.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisFollowFeedCache.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCache.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostCounterCache.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostSummaryCacheTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostDetailCacheTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisCommentPageCacheTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisFollowFeedCacheTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCacheTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostCounterCacheTest.java
git commit -m "feat: harden content redis cache payloads"
```

### Task 3: Content Hot-Path Single-Flight Port

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/HotPathSingleFlight.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisHotPathSingleFlight.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/NoopHotPathSingleFlight.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisHotPathSingleFlightTest.java`

**Interfaces:**
- Produces: `HotPathSingleFlight.execute(String scope, String key, Duration ttl, Supplier<T> loader, Supplier<T> fallbackWhenBusy)`.
- Redis implementation returns `fallbackWhenBusy.get()` when another node owns the lock.
- No-op implementation always executes `loader.get()`.

- [ ] **Step 1: Write Redis single-flight tests**

Create `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisHotPathSingleFlightTest.java`:

```java
package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.ContentHotPathProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisHotPathSingleFlightTest {

    @Test
    void executeShouldRunLoaderWhenLockIsAcquired() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("sf:hot-path:feed:key-1"), any(String.class), eq(Duration.ofMillis(100))))
                .thenReturn(true);
        RedisHotPathSingleFlight singleFlight = new RedisHotPathSingleFlight(redisTemplate, new ContentHotPathProperties());

        String result = singleFlight.execute("feed", "key-1", Duration.ofMillis(100), () -> "loaded", () -> "busy");

        assertThat(result).isEqualTo("loaded");
        verify(redisTemplate).execute(any(), eq(java.util.Collections.singletonList("sf:hot-path:feed:key-1")), any(String.class));
    }

    @Test
    void executeShouldUseBusyFallbackWhenLockIsHeld() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("sf:hot-path:feed:key-1"), any(String.class), eq(Duration.ofMillis(100))))
                .thenReturn(false);
        RedisHotPathSingleFlight singleFlight = new RedisHotPathSingleFlight(redisTemplate, new ContentHotPathProperties());
        AtomicInteger loaderCalls = new AtomicInteger();

        String result = singleFlight.execute("feed", "key-1", Duration.ofMillis(100), () -> {
            loaderCalls.incrementAndGet();
            return "loaded";
        }, () -> "busy");

        assertThat(result).isEqualTo("busy");
        assertThat(loaderCalls).hasValue(0);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=RedisHotPathSingleFlightTest
```

Expected: compilation fails because the single-flight types do not exist.

- [ ] **Step 3: Add application port**

Create `backend/community-app/src/main/java/com/nowcoder/community/content/application/HotPathSingleFlight.java`:

```java
package com.nowcoder.community.content.application;

import java.time.Duration;
import java.util.function.Supplier;

public interface HotPathSingleFlight {

    <T> T execute(String scope, String key, Duration ttl, Supplier<T> loader, Supplier<T> fallbackWhenBusy);
}
```

- [ ] **Step 4: Add no-op fallback implementation**

Create `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/NoopHotPathSingleFlight.java`:

```java
package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.HotPathSingleFlight;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.function.Supplier;

@Repository
@ConditionalOnMissingBean(HotPathSingleFlight.class)
public class NoopHotPathSingleFlight implements HotPathSingleFlight {

    @Override
    public <T> T execute(String scope, String key, Duration ttl, Supplier<T> loader, Supplier<T> fallbackWhenBusy) {
        return loader.get();
    }
}
```

- [ ] **Step 5: Add Redis implementation**

Create `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisHotPathSingleFlight.java`:

```java
package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.ContentHotPathProperties;
import com.nowcoder.community.content.application.HotPathSingleFlight;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

@Repository
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(name = "content.hot-path.single-flight.enabled", havingValue = "true", matchIfMissing = true)
public class RedisHotPathSingleFlight implements HotPathSingleFlight {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final ContentHotPathProperties properties;

    public RedisHotPathSingleFlight(StringRedisTemplate redisTemplate, ContentHotPathProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties == null ? new ContentHotPathProperties() : properties;
    }

    @Override
    public <T> T execute(String scope, String key, Duration ttl, Supplier<T> loader, Supplier<T> fallbackWhenBusy) {
        if (!StringUtils.hasText(scope) || !StringUtils.hasText(key) || loader == null) {
            return loader == null ? null : loader.get();
        }
        String lockKey = "sf:hot-path:" + scope.trim() + ":" + key.trim();
        String token = UUID.randomUUID().toString();
        Duration lockTtl = positive(ttl == null ? properties.getSingleFlight().ttl() : ttl);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, token, lockTtl);
        if (!Boolean.TRUE.equals(acquired)) {
            return fallbackWhenBusy == null ? null : fallbackWhenBusy.get();
        }
        try {
            return loader.get();
        } finally {
            redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), token);
        }
    }

    private static Duration positive(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            return Duration.ofMillis(1L);
        }
        return value;
    }
}
```

- [ ] **Step 6: Run task tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=RedisHotPathSingleFlightTest
```

Expected: selected test passes.

- [ ] **Step 7: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/application/HotPathSingleFlight.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisHotPathSingleFlight.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/NoopHotPathSingleFlight.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisHotPathSingleFlightTest.java
git commit -m "feat: add content hot path single flight"
```

### Task 4: Protect Feed And Detail Repository Fallback

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedReadApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostReadApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/FeedReadApplicationServiceReliabilityTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostReadApplicationServiceTest.java`

**Interfaces:**
- Consumes: `HotPathSingleFlight.execute(...)`.
- Consumes: `ContentHotPathProperties.getSingleFlight().ttl()`.
- Produces: feed single-flight busy response with no repository fallback call.
- Produces: detail shell single-flight guarded loader.

- [ ] **Step 1: Add feed single-flight tests**

In `FeedReadApplicationServiceReliabilityTest`, add:

```java
@Test
void listGlobalHotFeedShouldSkipRepositoryFallbackWhenSingleFlightIsBusy() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PostFeedCache postFeedCache = mock(PostFeedCache.class);
    PostContentRepository postContentRepository = mock(PostContentRepository.class);
    PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
    PostFeedSummaryLoader postFeedSummaryLoader = mock(PostFeedSummaryLoader.class);
    HotPathSingleFlight singleFlight = (scope, key, ttl, loader, fallbackWhenBusy) -> fallbackWhenBusy.get();
    when(postFeedCache.readGlobalHotIds("", 20)).thenReturn(List.of());

    FeedReadApplicationService service = service(
            postFeedCache,
            postContentRepository,
            postSummaryCache,
            postFeedSummaryLoader,
            registry,
            new ContentFeedPolicyProperties(),
            new ContentHotPathProperties(),
            singleFlight
    );

    FeedPageResult result = service.listGlobalHotFeed(null, "", 20);

    assertThat(result.items()).isEmpty();
    verifyNoInteractions(postContentRepository);
    assertThat(countMetric(registry, "singleflight_busy", "global")).isEqualTo(1.0);
}
```

Adjust the local `service(...)` helper to accept `ContentHotPathProperties` and `HotPathSingleFlight`.

- [ ] **Step 2: Add detail single-flight test**

In `PostReadApplicationServiceTest`, add:

```java
@Test
void getPostDetailShouldLoadShellThroughSingleFlightOnCacheMiss() {
    HotPathSingleFlight singleFlight = mock(HotPathSingleFlight.class);
    when(postDetailCache.get(postId)).thenReturn(null);
    when(singleFlight.execute(eq("post_detail"), eq(postId.toString()), any(), any(), any()))
            .thenAnswer(invocation -> {
                java.util.function.Supplier<PostDetailResult> loader = invocation.getArgument(3);
                return loader.get();
            });
    PostReadApplicationService service = serviceWithSingleFlight(singleFlight);

    PostDetailResult result = service.getPostDetail(null, postId);

    assertThat(result.id()).isEqualTo(postId);
    verify(singleFlight).execute(eq("post_detail"), eq(postId.toString()), any(), any(), any());
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=FeedReadApplicationServiceReliabilityTest,PostReadApplicationServiceTest
```

Expected: selected tests fail because application services do not use `HotPathSingleFlight`.

- [ ] **Step 4: Wire single-flight into feed reads**

Modify `FeedReadApplicationService`:

```java
private final ContentHotPathProperties hotPathProperties;
private final HotPathSingleFlight hotPathSingleFlight;
```

Constructor assignment:

```java
this.hotPathProperties = hotPathProperties == null ? new ContentHotPathProperties() : hotPathProperties;
this.hotPathSingleFlight = hotPathSingleFlight == null
        ? (scope, key, ttl, loader, fallbackWhenBusy) -> loader.get()
        : hotPathSingleFlight;
```

Wrap repository fallback:

```java
private LoadedFeedPage loadFallbackPage(int page, int limit, UUID boardId, String scope, boolean cacheDegraded) {
    String singleFlightKey = boardId == null
            ? "global:" + page + ":" + limit
            : "board:" + boardId + ":" + page + ":" + limit;
    return hotPathSingleFlight.execute(
            "feed",
            singleFlightKey,
            hotPathProperties.getSingleFlight().ttl(),
            () -> loadFallbackPageUnlocked(page, limit, boardId, scope, cacheDegraded),
            () -> {
                RankVersionResult rankVersion = safeRankVersion();
                hotFeedReadMetrics.record("singleflight_busy", scope);
                return new LoadedFeedPage(List.of(), false, rankVersion.value());
            }
    );
}
```

Move the current fallback body into `loadFallbackPageUnlocked(...)` without changing response semantics.

- [ ] **Step 5: Wire single-flight into detail reads**

Modify `PostReadApplicationService`:

```java
private final ContentHotPathProperties hotPathProperties;
private final HotPathSingleFlight hotPathSingleFlight;
```

On cache miss:

```java
PostDetailResult loaded = hotPathSingleFlight.execute(
        "post_detail",
        postId.toString(),
        hotPathProperties.getSingleFlight().ttl(),
        () -> loadPostDetailShell(postId),
        () -> loadPostDetailShell(postId)
);
postDetailCache.put(postId, loaded);
return applyViewerOverlay(currentUserId, applyCounterOverlay(loaded));
```

Detail fallback still loads when busy because returning an empty detail would change an existing required read contract.

- [ ] **Step 6: Run task tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=FeedReadApplicationServiceReliabilityTest,FeedReadApplicationServiceTest,PostReadApplicationServiceTest
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedReadApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/application/PostReadApplicationService.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/application/FeedReadApplicationServiceReliabilityTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/application/PostReadApplicationServiceTest.java
git commit -m "feat: protect content hot path fallback"
```

### Task 5: Hot Key Prewarm Application Service And Job

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/HotPathPrewarmApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/HotPathPrewarmResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/job/HotPathPrewarmJob.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/HotPathPrewarmApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/job/HotPathPrewarmJobTest.java`

**Interfaces:**
- Produces: `HotPathPrewarmApplicationService.prewarm()`.
- Produces: `HotPathPrewarmResult(int feedPages, int summaries, int details)`.
- Job calls only same-domain application service.

- [ ] **Step 1: Write application service tests**

Create `backend/community-app/src/test/java/com/nowcoder/community/content/application/HotPathPrewarmApplicationServiceTest.java`:

```java
package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.HotPathPrewarmResult;
import com.nowcoder.community.content.domain.model.Category;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CategoryContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HotPathPrewarmApplicationServiceTest {

    @Test
    void prewarmShouldWarmGlobalAndBoardHotFeed() {
        PostContentRepository postRepository = mock(PostContentRepository.class);
        CategoryContentRepository categoryRepository = mock(CategoryContentRepository.class);
        PostFeedCache feedCache = mock(PostFeedCache.class);
        PostSummaryCache summaryCache = mock(PostSummaryCache.class);
        PostFeedSummaryLoader summaryLoader = mock(PostFeedSummaryLoader.class);
        PostDetailCache detailCache = mock(PostDetailCache.class);
        ContentFeedPolicyProperties feedProperties = new ContentFeedPolicyProperties();
        ContentHotPathProperties hotPathProperties = new ContentHotPathProperties();
        hotPathProperties.getPrewarm().setPages(1);
        hotPathProperties.getPrewarm().setPageSize(2);
        hotPathProperties.getPrewarm().setBoardLimit(1);
        UUID boardId = uuid(9);
        DiscussPost post = post(uuid(1), boardId, 100.0);
        when(postRepository.listPosts(0, 2, PostContentRepository.ORDER_HOT)).thenReturn(List.of(post));
        when(categoryRepository.listCategories()).thenReturn(List.of(category(boardId)));
        when(postRepository.listPosts(0, 2, PostContentRepository.ORDER_HOT, boardId, null)).thenReturn(List.of(post));
        when(summaryLoader.assembleSummaries(List.of(post))).thenReturn(List.of(summary(post.getId())));
        HotPathPrewarmApplicationService service = new HotPathPrewarmApplicationService(
                postRepository,
                categoryRepository,
                feedCache,
                summaryCache,
                summaryLoader,
                detailCache,
                feedProperties,
                hotPathProperties,
                (scope, key, ttl, loader, fallback) -> loader.get()
        );

        HotPathPrewarmResult result = service.prewarm();

        assertThat(result.feedPages()).isEqualTo(2);
        assertThat(result.summaries()).isEqualTo(2);
        verify(feedCache).writeRankVersion("hot-v2");
        verify(feedCache).upsertGlobalHot(post.getId(), 100.0, "hot-v2");
        verify(feedCache).upsertBoardHot(boardId, post.getId(), 100.0, "hot-v2");
        verify(summaryCache).putAll(List.of(summary(post.getId())));
    }
}
```

Add private helpers in the test class for `uuid`, `post`, `category`, and `summary`.

- [ ] **Step 2: Write job tests**

Create `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/job/HotPathPrewarmJobTest.java`:

```java
package com.nowcoder.community.content.infrastructure.job;

import com.nowcoder.community.content.application.HotPathPrewarmApplicationService;
import com.nowcoder.community.content.application.result.HotPathPrewarmResult;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HotPathPrewarmJobTest {

    @Test
    void prewarmShouldSkipWhenDisabled() {
        HotPathPrewarmApplicationService service = mock(HotPathPrewarmApplicationService.class);
        HotPathPrewarmJob job = new HotPathPrewarmJob(service, false);

        job.prewarm();

        verify(service, never()).prewarm();
    }

    @Test
    void prewarmShouldDelegateToApplicationServiceWhenEnabled() {
        HotPathPrewarmApplicationService service = mock(HotPathPrewarmApplicationService.class);
        when(service.prewarm()).thenReturn(new HotPathPrewarmResult(1, 2, 3));
        HotPathPrewarmJob job = new HotPathPrewarmJob(service, true);

        job.prewarm();

        verify(service).prewarm();
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=HotPathPrewarmApplicationServiceTest,HotPathPrewarmJobTest
```

Expected: compilation fails because prewarm classes do not exist.

- [ ] **Step 4: Add result record**

Create `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/HotPathPrewarmResult.java`:

```java
package com.nowcoder.community.content.application.result;

public record HotPathPrewarmResult(
        int feedPages,
        int summaries,
        int details
) {
}
```

- [ ] **Step 5: Add prewarm application service**

Create `backend/community-app/src/main/java/com/nowcoder/community/content/application/HotPathPrewarmApplicationService.java`:

```java
package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.HotPathPrewarmResult;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.domain.model.Category;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CategoryContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class HotPathPrewarmApplicationService {

    private final PostContentRepository postRepository;
    private final CategoryContentRepository categoryRepository;
    private final PostFeedCache feedCache;
    private final PostSummaryCache summaryCache;
    private final PostFeedSummaryLoader summaryLoader;
    private final PostDetailCache detailCache;
    private final ContentFeedPolicyProperties feedProperties;
    private final ContentHotPathProperties hotPathProperties;
    private final HotPathSingleFlight singleFlight;

    public HotPathPrewarmApplicationService(
            PostContentRepository postRepository,
            CategoryContentRepository categoryRepository,
            PostFeedCache feedCache,
            PostSummaryCache summaryCache,
            PostFeedSummaryLoader summaryLoader,
            PostDetailCache detailCache,
            ContentFeedPolicyProperties feedProperties,
            ContentHotPathProperties hotPathProperties,
            HotPathSingleFlight singleFlight
    ) {
        this.postRepository = postRepository;
        this.categoryRepository = categoryRepository;
        this.feedCache = feedCache;
        this.summaryCache = summaryCache;
        this.summaryLoader = summaryLoader;
        this.detailCache = detailCache;
        this.feedProperties = feedProperties == null ? new ContentFeedPolicyProperties() : feedProperties;
        this.hotPathProperties = hotPathProperties == null ? new ContentHotPathProperties() : hotPathProperties;
        this.singleFlight = singleFlight == null ? (scope, key, ttl, loader, fallback) -> loader.get() : singleFlight;
    }

    public HotPathPrewarmResult prewarm() {
        return singleFlight.execute(
                "prewarm",
                "content-hot-path",
                hotPathProperties.getPrewarm().lockTtl(),
                this::prewarmUnlocked,
                () -> new HotPathPrewarmResult(0, 0, 0)
        );
    }

    private HotPathPrewarmResult prewarmUnlocked() {
        int pages = Math.max(1, hotPathProperties.getPrewarm().getPages());
        int pageSize = Math.max(1, Math.min(50, hotPathProperties.getPrewarm().getPageSize()));
        String rankVersion = feedProperties.getHotRankVersion();
        int feedPages = 0;
        int summaries = 0;
        int details = 0;
        feedCache.writeRankVersion(rankVersion);
        for (int page = 0; page < pages; page++) {
            List<DiscussPost> posts = safePosts(postRepository.listPosts(page, pageSize, PostContentRepository.ORDER_HOT));
            WarmCounts counts = warmPosts(posts, null, rankVersion);
            feedPages += posts.isEmpty() ? 0 : 1;
            summaries += counts.summaries();
            details += counts.details();
        }
        int boards = 0;
        for (Category category : safeCategories(categoryRepository.listCategories())) {
            if (category == null || category.getId() == null) {
                continue;
            }
            if (boards++ >= Math.max(0, hotPathProperties.getPrewarm().getBoardLimit())) {
                break;
            }
            for (int page = 0; page < pages; page++) {
                List<DiscussPost> posts = safePosts(postRepository.listPosts(page, pageSize, PostContentRepository.ORDER_HOT, category.getId(), null));
                WarmCounts counts = warmPosts(posts, category.getId(), rankVersion);
                feedPages += posts.isEmpty() ? 0 : 1;
                summaries += counts.summaries();
                details += counts.details();
            }
        }
        return new HotPathPrewarmResult(feedPages, summaries, details);
    }

    private WarmCounts warmPosts(List<DiscussPost> posts, UUID boardId, String rankVersion) {
        if (posts.isEmpty()) {
            return new WarmCounts(0, 0);
        }
        List<PostSummaryResult> summaryResults = summaryLoader.assembleSummaries(posts);
        summaryCache.putAll(summaryResults);
        int detailCount = 0;
        for (DiscussPost post : posts) {
            if (post == null || post.getId() == null) {
                continue;
            }
            if (boardId == null) {
                feedCache.upsertGlobalHot(post.getId(), post.getScore(), rankVersion);
            } else {
                feedCache.upsertBoardHot(boardId, post.getId(), post.getScore(), rankVersion);
            }
            detailCount++;
        }
        return new WarmCounts(summaryResults == null ? 0 : summaryResults.size(), detailCount);
    }

    private static List<DiscussPost> safePosts(List<DiscussPost> posts) {
        return posts == null ? List.of() : posts;
    }

    private static List<Category> safeCategories(List<Category> categories) {
        return categories == null ? List.of() : new ArrayList<>(categories);
    }

    private record WarmCounts(int summaries, int details) {
    }
}
```

- [ ] **Step 6: Add scheduled job**

Create `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/job/HotPathPrewarmJob.java`:

```java
package com.nowcoder.community.content.infrastructure.job;

import com.nowcoder.community.common.trace.TraceJobRunner;
import com.nowcoder.community.content.application.HotPathPrewarmApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HotPathPrewarmJob {

    private static final Logger log = LoggerFactory.getLogger(HotPathPrewarmJob.class);
    private static final String JOB_NAME = "content-hot-path-prewarm";

    private final HotPathPrewarmApplicationService hotPathPrewarmApplicationService;
    private final boolean enabled;

    public HotPathPrewarmJob(
            HotPathPrewarmApplicationService hotPathPrewarmApplicationService,
            @Value("${content.hot-path.prewarm.enabled:true}") boolean enabled
    ) {
        this.hotPathPrewarmApplicationService = hotPathPrewarmApplicationService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${content.hot-path.prewarm.delay-ms:60000}")
    public void prewarm() {
        TraceJobRunner.run(JOB_NAME, () -> {
            if (!enabled) {
                return;
            }
            try {
                hotPathPrewarmApplicationService.prewarm();
            } catch (RuntimeException e) {
                log.warn("[content-hot-path] prewarm failed: {}", e.toString());
            }
        });
    }
}
```

- [ ] **Step 7: Run task tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=HotPathPrewarmApplicationServiceTest,HotPathPrewarmJobTest
```

Expected: selected tests pass.

- [ ] **Step 8: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/application/HotPathPrewarmApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/application/result/HotPathPrewarmResult.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/job/HotPathPrewarmJob.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/application/HotPathPrewarmApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/job/HotPathPrewarmJobTest.java
git commit -m "feat: prewarm content hot path caches"
```

### Task 6: Hot-Path k6 Scenario And Documentation

**Files:**
- Create: `tests/k6/scenarios/hot-path.js`
- Modify: `tests/k6/package.json`
- Modify: `tests/k6/README.md`
- Modify: `docs/handbook/performance-testing.md`
- Modify: `docs/handbook/reliability.md`
- Modify: `docs/handbook/operations.md`
- Modify: `docs/handbook/observability.md`

**Interfaces:**
- Produces npm script: `npm run hot-path`.
- Produces k6 scenario export: `hotpath`.
- Documents bounded metric values and local capacity acceptance.

- [ ] **Step 1: Add hot-path k6 scenario**

Create `tests/k6/scenarios/hot-path.js`:

```javascript
import { buildOptions } from '../config/options.js'
import { config } from '../lib/config.js'
import { get, randomThinkTime } from '../lib/http.js'

export const options = buildOptions('hot-path', { exec: 'hotpath' })

function optionalGet(path) {
  if (path) {
    get(path)
  }
}

export function hotpath() {
  get(`/api/feed/global?size=${config.readSize}`)

  const boardId = __ENV.K6_BOARD_ID || ''
  if (boardId) {
    optionalGet(`/api/boards/${boardId}/feed?size=${config.readSize}`)
  }

  const postId = __ENV.K6_POST_ID || ''
  if (postId) {
    optionalGet(`/api/posts/${postId}`)
  }

  randomThinkTime()
}

export default hotpath
```

- [ ] **Step 2: Add npm script**

Modify `tests/k6/package.json` scripts:

```json
"hot-path": "node ./scripts/run-k6.js scenarios/hot-path.js"
```

- [ ] **Step 3: Add k6 README section**

Add to `tests/k6/README.md`:

~~~markdown
## Hot Path Scenario

Run:

```bash
npm run hot-path
```

The scenario always calls `/api/feed/global?size=<K6_READ_SIZE>`.
Set `K6_BOARD_ID=<uuid>` to include `/api/boards/{boardId}/feed`.
Set `K6_POST_ID=<uuid>` to include `/api/posts/{postId}`.

Use it after prewarm runs and after Redis flush/restart drills to compare hit,
fallback, degraded, and single-flight behavior through
`community_cache_requests_total`.
```
~~~

- [ ] **Step 4: Update performance testing handbook**

Add to `docs/handbook/performance-testing.md`:

~~~markdown
## Hot Path Capacity

Use the hot-path scenario for P3 feed/detail cache work:

```bash
cd tests/k6
K6_BOARD_ID=<board-uuid> K6_POST_ID=<post-uuid> npm run hot-path
```

Local cluster acceptance gates:

- hot feed/detail HTTP error rate `< 1%`
- hot path p95 `< 800ms`
- hot path p99 `< 1500ms`
- no sustained Hikari pending pressure during warm-cache runs
- Redis failures produce `degraded` or `singleflight_busy` cache outcomes instead of broad HTTP failure spikes
```
~~~

- [ ] **Step 5: Update reliability, operations, and observability docs**

Add bounded P3-A notes:

```markdown
### P3 Hot Path Cache Protection

P3-A protects hot feed and post detail reads with hot key prewarm, TTL jitter,
single-flight origin protection, and poison payload cleanup. These mechanisms
do not make Redis a business fact source. On Redis failure, feed reads may use
latest fallback or return an empty degraded page; post detail reads still load
from owner repositories when no safe cached response exists.
```

Add observability values:

```markdown
Additional allowed `community_cache_requests_total.result` values for P3-A:

- `singleflight_busy`
- `singleflight_error`
- `poison_cleanup`
- `prewarm`
```

- [ ] **Step 6: Run documentation and k6 static checks**

Run:

```bash
git diff --check -- docs/handbook tests/k6
cd tests/k6
npm test
```

Expected: whitespace check and k6 npm test pass.

- [ ] **Step 7: Commit**

```bash
git add tests/k6/scenarios/hot-path.js \
        tests/k6/package.json \
        tests/k6/README.md \
        docs/handbook/performance-testing.md \
        docs/handbook/reliability.md \
        docs/handbook/operations.md \
        docs/handbook/observability.md
git commit -m "test: add hot path capacity scenario"
```

### Task 7: Final Verification

**Files:**
- No new source files.
- Verify all files changed by Tasks 1-6.

**Interfaces:**
- Confirms P3-A implementation meets the spec and does not expand into P1/P2.

- [ ] **Step 1: Run focused backend tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='CacheTtlPolicyTest,HotFeedReadMetricsTest,FeedReadApplicationServiceReliabilityTest,FeedReadApplicationServiceTest,PostReadApplicationServiceTest,HotPathPrewarmApplicationServiceTest,HotPathPrewarmJobTest,RedisHotPathSingleFlightTest,RedisPostSummaryCacheTest,RedisPostDetailCacheTest,RedisCommentPageCacheTest,RedisFollowFeedCacheTest,RedisPostFeedCacheTest,RedisPostCounterCacheTest,PostHotFeedProjectionApplicationServiceTest,PostCounterApplicationServiceTest'
```

Expected: all selected tests pass.

- [ ] **Step 2: Run architecture guardrails**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Expected: all ArchUnit tests pass.

- [ ] **Step 3: Run k6 static validation**

Run:

```bash
cd tests/k6
npm test
```

Expected: k6 suite static checks pass.

- [ ] **Step 4: Inspect diff for forbidden scope**

Run:

```bash
git diff --name-only origin/main...HEAD
```

Expected: no new `/api/ops/**` controller files, no outbox governance changes, no root legacy `service` additions, and no P1/P2 implementation files.

- [ ] **Step 5: Commit final doc status if needed**

If implementation changed P3-A status text in docs, commit only those doc updates:

```bash
git add docs/handbook docs/superpowers/specs/2026-07-07-bbs-reliability-p3-hotpath-optimization-design.md
git commit -m "docs: document p3 hot path optimization status"
```

## Self-Review

Spec coverage:

- Hot key prewarm is covered by Task 5.
- TTL jitter is covered by Tasks 1 and 2.
- Single-flight origin protection is covered by Tasks 3 and 4.
- Payload poison cleanup is covered by Task 2.
- Rank version switching is explicitly preserved and scoped to P3-B by the spec; Task 7 verifies existing rank guard tests still pass.
- Counter consistency repair is explicitly scoped to P3-B by the spec; Task 7 verifies existing counter overlay/flush tests still pass.
- Load and capacity acceptance is covered by Task 6.

Placeholder scan:

- This plan avoids unresolved placeholder markers and does not ask implementers to invent missing error handling.
- Each task includes exact file paths, expected test commands, and concrete code shapes.

Type consistency:

- `ContentHotPathProperties`, `CacheTtlPolicy`, `HotFeedReadMetrics.recordCache`, and `HotPathSingleFlight.execute` are introduced before later tasks consume them.
- `HotPathPrewarmResult` is introduced before `HotPathPrewarmJob` uses the prewarm application service result.
- k6 exports `hotpath`, matching the `exec: 'hotpath'` option.
