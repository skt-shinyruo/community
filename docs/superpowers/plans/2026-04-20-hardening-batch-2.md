# Hardening Batch 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix remaining Redis atomicity issues, add search reindex lock renewal, and remove JWT placeholder defaults so all services can start safely under the new JWT secret validation.

**Architecture:** Use Redis Lua scripts for atomic "write + expire" and "check marker + write" operations, and add a scheduled lock-renewal loop for long-running search reindex jobs. Keep changes small and add regression tests that assert the presence/shape of the Lua scripts and renewal behavior.

**Tech Stack:** Java 17, Spring Boot 3.2.x, Spring Data Redis (`StringRedisTemplate`), JUnit 5, Mockito, Maven multi-module (`backend` aggregator).

---

### Task 1: Search Reindex Distributed Lock Renewal

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/infra/scheduler/SingleFlightTaskGuard.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/service/ReindexJobService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchReindexExecutionService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/service/ReindexJobServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
@ExtendWith(MockitoExtension.class)
class ReindexJobServiceTest {
  @Mock StringRedisTemplate redisTemplate;

  @Test
  void startRenewal_shouldRefreshLockTtlPeriodically() throws Exception {
    SingleFlightTaskGuard guard = new SingleFlightTaskGuard(redisTemplate);
    ReindexJobService service = new ReindexJobService(guard, Duration.ofSeconds(2));
    ReindexJobService.ReindexJob job =
        new ReindexJobService.ReindexJob("job", true, new SingleFlightTaskGuard.Lock("sf:task:search:reindex", "t"));

    when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
        .thenReturn(1L);

    try (ReindexJobService.RenewalHandle handle = service.startRenewal(job)) {
      Thread.sleep(800);
    }

    verify(redisTemplate, atLeastOnce()).execute(any(RedisScript.class), eq(List.of("sf:task:search:reindex")), eq("t"), any());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
cd backend
mvn -pl community-app -Dtest=ReindexJobServiceTest test
```
Expected: FAIL (no renewal calls).

- [ ] **Step 3: Implement minimal renewal + refresh API**

Implementation notes:
- Add a `refresh(Lock, Duration)` method to `SingleFlightTaskGuard` using a compare-and-`PEXPIRE` Lua script.
- Implement `ReindexJobService.startRenewal(job)` to start a daemon `ScheduledExecutorService` refreshing every `lockTtl/3` (min 1s).
- In `SearchReindexExecutionService.reindex()`, wrap the reindex execution with try-with-resources:

```java
try (ReindexJobService.RenewalHandle ignored = reindexJobService.startRenewal(job)) {
  int count = postSearchService.clearAndReindexFromContentService();
  ...
} finally {
  reindexJobService.finish(job);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl community-app -Dtest=ReindexJobServiceTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/infra/scheduler/SingleFlightTaskGuard.java \
        backend/community-app/src/main/java/com/nowcoder/community/search/service/ReindexJobService.java \
        backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchReindexExecutionService.java \
        backend/community-app/src/test/java/com/nowcoder/community/search/service/ReindexJobServiceTest.java
git commit -m "fix(search): renew reindex single-flight lock"
```

---

### Task 2: Redis Post Score Retry Hash TTL Must Be Atomic

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/score/RedisPostScoreQueue.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/score/RedisPostScoreQueueTest.java`

- [ ] **Step 1: Write the failing test**

```java
@ExtendWith(MockitoExtension.class)
class RedisPostScoreQueueTest {
  @Mock StringRedisTemplate redisTemplate;
  @SuppressWarnings("unchecked")
  @Mock ZSetOperations<String, String> zsetOps;
  @SuppressWarnings("unchecked")
  @Mock HashOperations<String, Object, Object> hashOps;

  @Test
  void reenqueue_shouldUseAtomicHincrbyAndExpireScript() {
    when(redisTemplate.opsForZSet()).thenReturn(zsetOps);
    when(redisTemplate.opsForHash()).thenReturn(hashOps);
    when(hashOps.increment(anyString(), any(), anyLong())).thenReturn(1L);

    RedisPostScoreQueue queue = new RedisPostScoreQueue(redisTemplate);
    queue.reenqueue(123);

    verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("post:score:retry")), eq("123"), any());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
cd backend
mvn -pl community-app -Dtest=RedisPostScoreQueueTest test
```
Expected: FAIL (implementation uses `opsForHash().increment()` + `expire()` instead of script).

- [ ] **Step 3: Implement minimal atomic script**

Implementation notes:
- Replace `opsForHash().increment()` + best-effort `expire()` with a Lua script:

```lua
local v = redis.call('hincrby', KEYS[1], ARGV[1], 1)
if v == 1 then redis.call('pexpire', KEYS[1], ARGV[2]) end
return v
```

Use `RETRY_HASH_TTL.toMillis()` for `ARGV[2]`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl community-app -Dtest=RedisPostScoreQueueTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/score/RedisPostScoreQueue.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/score/RedisPostScoreQueueTest.java
git commit -m "fix(content): atomic retry hash ttl for post score queue"
```

---

### Task 3: Analytics Union Temp Key TTL Must Be Atomic

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/RedisAnalyticsRepository.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/analytics/repo/RedisAnalyticsRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

```java
@ExtendWith(MockitoExtension.class)
class RedisAnalyticsRepositoryTest {
  @Mock StringRedisTemplate redisTemplate;
  @SuppressWarnings("unchecked")
  @Mock HyperLogLogOperations<String, String> hllOps;

  @Test
  void calculateUv_shouldUseUnionPlusExpireScript() {
    when(redisTemplate.opsForHyperLogLog()).thenReturn(hllOps);
    when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(null);
    when(redisTemplate.opsForHyperLogLog().size(anyString())).thenReturn(1L);
    when(hllOps.union(anyString(), any(String[].class))).thenReturn(1L);

    RedisAnalyticsRepository repo = new RedisAnalyticsRepository(redisTemplate);
    repo.calculateUv(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));

    verify(redisTemplate).execute(any(RedisScript.class), anyList(), any());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
cd backend
mvn -pl community-app -Dtest=RedisAnalyticsRepositoryTest test
```
Expected: FAIL (current code uses separate `union`/`bitOp` and `expire`).

- [ ] **Step 3: Implement minimal scripts**

Implementation notes:
- Replace `union(...)` + `expire(...)` with one Lua script that runs `PFMERGE` then `PEXPIRE`.
- Replace `bitOp(OR, ...)` + `expire(...)` with one Lua script that runs `BITOP OR` then `PEXPIRE`.
- Keep `finally safeDelete(unionKey)` as a best-effort cleanup.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl community-app -Dtest=RedisAnalyticsRepositoryTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/RedisAnalyticsRepository.java \
        backend/community-app/src/test/java/com/nowcoder/community/analytics/repo/RedisAnalyticsRepositoryTest.java
git commit -m "fix(analytics): atomic expire for redis union temp keys"
```

---

### Task 4: Remove JWT Placeholder Defaults From Service YAMLs

**Files:**
- Modify: `backend/community-app/src/main/resources/application.yml`
- Modify: `backend/community-gateway/src/main/resources/application.yml`
- Modify: `backend/community-im/im-realtime/src/main/resources/application.yml`
- (Optional) Modify: `README.md`

- [ ] **Step 1: Change defaults to require explicit `JWT_HMAC_SECRET`**

Example:
```yaml
security:
  jwt:
    hmac-secret: ${JWT_HMAC_SECRET:}
```

- [ ] **Step 2: Run module tests**

Run:
```bash
cd backend
mvn -pl community-common,community-gateway,community-im,community-app test
```
Expected: PASS (tests set `security.jwt.hmac-secret` explicitly where needed).

- [ ] **Step 3: Commit**

```bash
git add backend/community-app/src/main/resources/application.yml \
        backend/community-gateway/src/main/resources/application.yml \
        backend/community-im/im-realtime/src/main/resources/application.yml \
        README.md
git commit -m "chore(security): remove jwt placeholder defaults"
```

---

### Task 5: Redis Refresh Token Family Revoke Must Prevent Concurrent New Tokens

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RedisRefreshTokenStore.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RefreshTokenService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/RedisRefreshTokenStoreTest.java`

- [ ] **Step 1: Write the failing test**

```java
@ExtendWith(MockitoExtension.class)
class RedisRefreshTokenStoreTest {
  @Mock StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void store_shouldFailWhenFamilyIsRevokedMarkerExists() {
    JwtProperties props = new JwtProperties();
    RedisRefreshTokenStore store = new RedisRefreshTokenStore(redisTemplate, objectMapper, props);
    when(redisTemplate.hasKey("auth:refresh:family:revoked:f1")).thenReturn(true);

    store.store("t1", 1, "f1", Instant.now().plusSeconds(60));
    // expect exception or explicit contract (decide in implementation)
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl community-app -Dtest=RedisRefreshTokenStoreTest test`
Expected: FAIL.

- [ ] **Step 3: Implement marker + atomic store**

Implementation notes:
- Add revoked marker key: `auth:refresh:family:revoked:<familyId>` (TTL ~= refresh token TTL).
- Use Lua script to store token record + add to family set only when revoked marker does NOT exist.
- Update `RefreshTokenService.rotate(...)` to catch store failures and return `null` (so refresh endpoint returns invalid).

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl community-app -Dtest=RedisRefreshTokenStoreTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/service/RedisRefreshTokenStore.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/service/RefreshTokenService.java \
        backend/community-app/src/test/java/com/nowcoder/community/auth/service/RedisRefreshTokenStoreTest.java
git commit -m "fix(auth): prevent refresh token family revoke race in redis"
```

