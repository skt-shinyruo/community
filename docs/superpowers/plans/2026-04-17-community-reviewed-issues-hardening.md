# Community Reviewed Issues Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the four reviewed production risks by making search reindex single-flight across instances, hardening login throttling, aligning frontend local endpoint resolution with documented topology, and moving gateway rate limiting to shared Redis state.

**Architecture:** Keep the work split into four independently shippable task groups that map 1:1 to the reviewed issues. Reuse existing Redis-based infrastructure where it already exists (`SingleFlightTaskGuard`, Spring Data Redis), keep frontend fallback inference centralized in `endpointResolution.js`, and preserve existing controller/client contracts so the change surface stays local.

**Tech Stack:** Java 17, Spring Boot 3, Spring Data Redis, Spring Security, Maven Surefire, Vue 3, Vite, Vitest

---

## File Structure

- `backend/community-app/src/main/java/com/nowcoder/community/search/service/ReindexJobService.java`
  Owns reindex job acquisition/release and will switch from in-memory process state to Redis-backed single-flight.
- `backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchReindexExecutionService.java`
  Uses `ReindexJobService` and must release the acquired distributed lock handle instead of releasing by jobId string only.
- `backend/community-app/src/main/resources/application.yml`
  Adds the explicit reindex lock TTL property and removes the obsolete login throttling timeout property.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/service/LoginRateLimitService.java`
  Owns rate limit reads/writes, error handling, metrics, and will move to synchronous fail-closed behavior.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/config/LoginRateLimitProperties.java`
  Removes the no-longer-used async timeout property so config matches implementation.
- `frontend/src/config/endpointResolution.js`
  Becomes the single place that infers local API / IM HTTP / IM WS gateway URLs for `localhost` and `127.0.0.1`.
- `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/RateLimiter.java`
  New abstraction so the web filter depends on shared rate-limiter behavior instead of an in-memory implementation.
- `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/RedisRateLimiter.java`
  New shared-state limiter backed by Redis `INCR` + `EXPIRE`.
- `backend/community-gateway/src/main/resources/application.yml`
  Adds Redis connection defaults for single-node development and keeps existing rate-limit properties.
- `backend/community-gateway/src/main/resources/application-redis-cluster.yml`
  New profile file for cluster Redis nodes, mirroring the backend app pattern.
- `deploy/compose.runtime.services.single.yml`
  Supplies `SPRING_DATA_REDIS_HOST` to `community-gateway` in single-topology compose.
- `deploy/compose.runtime.services.cluster.yml`
  Supplies `SPRING_DATA_REDIS_CLUSTER_NODES` and enables `redis-cluster` profile for gateway replicas.

### Task 1: Redis-backed Reindex Single-Flight

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/service/ReindexJobService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchReindexExecutionService.java`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/service/ReindexJobServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/service/SearchReindexExecutionServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/service/SearchAdminServiceTest.java`

- [ ] **Step 1: Write the failing tests for distributed lock acquisition and release**

```java
package com.nowcoder.community.search.service;

import com.nowcoder.community.infra.scheduler.SingleFlightTaskGuard;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReindexJobServiceTest {

    @Test
    void tryStartShouldReturnAcquiredJobWhenDistributedLockSucceeds() {
        SingleFlightTaskGuard guard = mock(SingleFlightTaskGuard.class);
        SingleFlightTaskGuard.Lock lock = new SingleFlightTaskGuard.Lock("sf:task:search:reindex", "token-1");
        when(guard.tryAcquire("search:reindex", Duration.ofMinutes(30))).thenReturn(lock);

        ReindexJobService service = new ReindexJobService(guard, Duration.ofMinutes(30));

        ReindexJobService.ReindexJob job = service.tryStart();

        assertThat(job.acquired()).isTrue();
        assertThat(job.jobId()).isNotBlank();
        assertThat(job.lock()).isEqualTo(lock);
    }

    @Test
    void finishShouldReleaseDistributedLockFromJobHandle() {
        SingleFlightTaskGuard guard = mock(SingleFlightTaskGuard.class);
        SingleFlightTaskGuard.Lock lock = new SingleFlightTaskGuard.Lock("sf:task:search:reindex", "token-1");
        when(guard.tryAcquire("search:reindex", Duration.ofMinutes(30))).thenReturn(lock);

        ReindexJobService service = new ReindexJobService(guard, Duration.ofMinutes(30));
        ReindexJobService.ReindexJob job = service.tryStart();

        service.finish(job);

        verify(guard).release(lock);
    }
}
```

```java
package com.nowcoder.community.search.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SearchReindexExecutionServiceTest {

    @Test
    void executeShouldReleaseDistributedJobHandleWhenAcquired() {
        PostSearchService postSearchService = mock(PostSearchService.class);
        ReindexJobService reindexJobService = mock(ReindexJobService.class);
        ReindexJobService.ReindexJob job = new ReindexJobService.ReindexJob("job-1", true, null);
        when(reindexJobService.tryStart()).thenReturn(job);
        when(postSearchService.clearAndReindexFromContentService()).thenReturn(42);

        SearchReindexExecutionService service = new SearchReindexExecutionService(postSearchService, reindexJobService);
        service.reindex();

        verify(reindexJobService).finish(job);
        verifyNoMoreInteractions(reindexJobService);
    }
}
```

```java
package com.nowcoder.community.search.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.search.api.action.SearchReindexActionApi;
import com.nowcoder.community.search.api.model.SearchReindexResult;
import com.nowcoder.community.search.exception.SearchErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SearchAdminServiceTest {

    @Test
    void reindexShouldInvokeConflictWithoutJobIdWhenExecutionWasSkipped() {
        SearchReindexActionApi searchReindexActionApi = mock(SearchReindexActionApi.class);
        ReindexJobService reindexJobService = mock(ReindexJobService.class);
        when(searchReindexActionApi.reindex()).thenReturn(new SearchReindexResult(null, 0, true, "reindex 任务正在执行"));
        doThrow(new BusinessException(SearchErrorCode.REINDEX_RUNNING, "reindex 任务正在执行"))
                .when(reindexJobService).conflict(null);

        SearchAdminService service = new SearchAdminService(searchReindexActionApi, reindexJobService);

        assertThatThrownBy(service::reindex)
                .isInstanceOf(BusinessException.class)
                .hasMessage("reindex 任务正在执行");

        verify(searchReindexActionApi).reindex();
        verify(reindexJobService).conflict(null);
        verifyNoMoreInteractions(searchReindexActionApi, reindexJobService);
    }
}
```

- [ ] **Step 2: Run the search reindex tests to verify they fail for the right reason**

Run: `mvn -pl :community-app -Dtest=ReindexJobServiceTest,SearchReindexExecutionServiceTest,SearchAdminServiceTest test`

Expected: FAIL with compile/runtime errors because `ReindexJobService` does not yet accept `SingleFlightTaskGuard`, `ReindexJob.lock()` does not exist, `SearchReindexExecutionService` still calls `finish(job.jobId())`, and `SearchAdminServiceTest` still depends on the old no-arg `ReindexJobService` shape.

- [ ] **Step 3: Write the minimal implementation for Redis-backed single-flight**

```java
package com.nowcoder.community.search.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.infra.scheduler.SingleFlightTaskGuard;
import com.nowcoder.community.search.exception.SearchErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

@Service
public class ReindexJobService {

    private static final String TASK_NAME = "search:reindex";

    private final SingleFlightTaskGuard singleFlightTaskGuard;
    private final Duration lockTtl;

    public ReindexJobService(
            ObjectProvider<SingleFlightTaskGuard> singleFlightTaskGuardProvider,
            @Value("${search.reindex.lock-ttl:30m}") Duration lockTtl
    ) {
        this(singleFlightTaskGuardProvider == null ? null : singleFlightTaskGuardProvider.getIfAvailable(),
                lockTtl == null || lockTtl.isNegative() || lockTtl.isZero() ? Duration.ofMinutes(30) : lockTtl);
    }

    ReindexJobService(SingleFlightTaskGuard singleFlightTaskGuard, Duration lockTtl) {
        this.singleFlightTaskGuard = singleFlightTaskGuard;
        this.lockTtl = lockTtl == null || lockTtl.isNegative() || lockTtl.isZero() ? Duration.ofMinutes(30) : lockTtl;
    }

    public ReindexJob tryStart() {
        String jobId = newJobId();
        SingleFlightTaskGuard.Lock lock = singleFlightTaskGuard == null ? null : singleFlightTaskGuard.tryAcquire(TASK_NAME, lockTtl);
        if (lock == null) {
            return new ReindexJob(null, false, null);
        }
        return new ReindexJob(jobId, true, lock);
    }

    public RenewalHandle startRenewal(ReindexJob job) {
        return () -> { };
    }

    public void finish(ReindexJob job) {
        if (job == null || job.lock() == null || singleFlightTaskGuard == null) {
            return;
        }
        singleFlightTaskGuard.release(job.lock());
    }

    public void conflict(String jobId) {
        String suffix = StringUtils.hasText(jobId) ? (" (jobId=" + jobId.trim() + ")") : "";
        throw new BusinessException(SearchErrorCode.REINDEX_RUNNING, "reindex 任务正在执行" + suffix);
    }

    private String newJobId() {
        try {
            return UUID.randomUUID().toString();
        } catch (RuntimeException e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    @FunctionalInterface
    public interface RenewalHandle extends AutoCloseable {
        void stop();

        @Override
        default void close() {
            stop();
        }
    }

    public record ReindexJob(String jobId, boolean acquired, SingleFlightTaskGuard.Lock lock) {
    }
}
```

```java
package com.nowcoder.community.search.service;

// inside SearchReindexExecutionService.reindex()
ReindexJobService.ReindexJob job = reindexJobService.tryStart();
if (job == null || !job.acquired()) {
    return new SearchReindexResult(job == null ? null : job.jobId(), 0, true, skippedReason(job));
}
try {
    int count = postSearchService.clearAndReindexFromContentService();
    return new SearchReindexResult(job.jobId(), count, false, null);
} finally {
    reindexJobService.finish(job);
}
```

```yaml
search:
  storage: es
  reindex:
    lock-ttl: 30m
```

- [ ] **Step 4: Run the search reindex tests again to verify they pass**

Run: `mvn -pl :community-app -Dtest=ReindexJobServiceTest,SearchReindexExecutionServiceTest,SearchAdminServiceTest test`

Expected: PASS, with `SearchReindexExecutionServiceTest` still confirming that successful and failed execution paths release the acquired job handle.

- [ ] **Step 5: Commit the reindex hardening**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/search/service/ReindexJobService.java \
  backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchReindexExecutionService.java \
  backend/community-app/src/main/resources/application.yml \
  backend/community-app/src/test/java/com/nowcoder/community/search/service/ReindexJobServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/search/service/SearchReindexExecutionServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/search/service/SearchAdminServiceTest.java
git commit -m "fix: make search reindex single-flight across instances"
```

### Task 2: Fail-Closed Login Rate Limiting

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/LoginRateLimitService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/config/LoginRateLimitProperties.java`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/LoginRateLimitServiceTest.java`

- [ ] **Step 1: Write the failing tests for synchronous fail-closed behavior**

```java
package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.LoginRateLimitProperties;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginRateLimitServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<MeterRegistry> meterRegistryProvider = mock(ObjectProvider.class);

    private LoginRateLimitService service;

    @BeforeEach
    void setUp() {
        LoginRateLimitProperties properties = new LoginRateLimitProperties();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new LoginRateLimitService(properties, redisTemplate, meterRegistryProvider);
    }

    @Test
    void assertNotBlockedShouldFailClosedWhenRedisReadThrows() {
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> service.assertNotBlocked("alice", "127.0.0.1", "remote"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void isCaptchaRequiredShouldReturnTrueWhenRedisReadThrows() {
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThat(service.isCaptchaRequired("alice", "127.0.0.1")).isTrue();
    }
}
```

- [ ] **Step 2: Run the login rate limit tests to verify they fail**

Run: `mvn -pl :community-app -Dtest=LoginRateLimitServiceTest test`

Expected: FAIL because the current implementation swallows Redis failures, uses async timeout wrappers, and returns `false` from `isCaptchaRequired()` when dependency access fails.

- [ ] **Step 3: Write the minimal synchronous fail-closed implementation**

```java
package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.LoginRateLimitProperties;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class LoginRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitService.class);

    // keep KEY_PREFIX*, METRIC, constructor, and metrics helper as-is

    public void assertNotBlocked(String username, String ip, String ipSource) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            int ipLimit = Math.max(1, properties.getMaxFailuresPerIp());
            int userLimit = Math.max(1, properties.getMaxFailuresPerUser());
            if (StringUtils.hasText(ip) && getCount(KEY_PREFIX_IP + ip.trim()) >= ipLimit) {
                record("blocked", ipSource);
                throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试");
            }
            if (StringUtils.hasText(username) && getCount(KEY_PREFIX_USER + normalizeUsername(username)) >= userLimit) {
                record("blocked", ipSource);
                throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "账号登录尝试过于频繁，请稍后再试");
            }
            record("allowed", ipSource);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            record("dependency_error", ipSource);
            log.warn("[auth][login-rate-limit] assertNotBlocked failed: {}", e.toString());
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "登录风控暂时不可用，请稍后重试");
        }
    }

    public void recordFailure(String username, String ip, String ipSource) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            int ipLimit = Math.max(1, properties.getMaxFailuresPerIp());
            int userLimit = Math.max(1, properties.getMaxFailuresPerUser());
            if (StringUtils.hasText(ip) && increment(KEY_PREFIX_IP + ip.trim()) >= ipLimit) {
                record("blocked", ipSource);
                throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试");
            }
            if (StringUtils.hasText(username) && increment(KEY_PREFIX_USER + normalizeUsername(username)) >= userLimit) {
                record("blocked", ipSource);
                throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "账号登录尝试过于频繁，请稍后再试");
            }
            record("allowed", ipSource);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            record("dependency_error", ipSource);
            log.warn("[auth][login-rate-limit] recordFailure failed: {}", e.toString());
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "登录风控暂时不可用，请稍后重试");
        }
    }

    public boolean isCaptchaRequired(String username, String ip) {
        if (!properties.isEnabled()) {
            return false;
        }
        try {
            int ipThreshold = properties.getCaptchaRequiredFailuresPerIp();
            int userThreshold = properties.getCaptchaRequiredFailuresPerUser();
            if (StringUtils.hasText(ip) && (ipThreshold <= 0 || getCount(KEY_PREFIX_IP + ip.trim()) >= ipThreshold)) {
                return true;
            }
            if (StringUtils.hasText(username) && (userThreshold <= 0 || getCount(KEY_PREFIX_USER + normalizeUsername(username)) >= userThreshold)) {
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            record("dependency_error", null);
            log.warn("[auth][login-rate-limit] isCaptchaRequired failed: {}", e.toString());
            return true;
        }
    }

    public void reset(String username, String ip) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            if (StringUtils.hasText(ip)) {
                redisTemplate.delete(KEY_PREFIX_IP + ip.trim());
            }
            if (StringUtils.hasText(username)) {
                redisTemplate.delete(KEY_PREFIX_USER + normalizeUsername(username));
            }
        } catch (RuntimeException e) {
            record("dependency_error", null);
            log.warn("[auth][login-rate-limit] reset failed: {}", e.toString());
        }
    }
}
```

```java
package com.nowcoder.community.auth.config;

@ConfigurationProperties(prefix = "auth.login-rate-limit")
public class LoginRateLimitProperties {
    private boolean enabled = true;
    private int windowSeconds = 60;
    private int maxFailuresPerIp = 20;
    private int maxFailuresPerUser = 5;
    private int captchaRequiredFailuresPerIp = 5;
    private int captchaRequiredFailuresPerUser = 2;
}
```

```yaml
auth:
  login-rate-limit:
    enabled: true
    window-seconds: 60
    max-failures-per-ip: 20
    max-failures-per-user: 5
    captcha-required-failures-per-ip: 5
    captcha-required-failures-per-user: 2
```

- [ ] **Step 4: Run the login rate limit tests again to verify they pass**

Run: `mvn -pl :community-app -Dtest=LoginRateLimitServiceTest test`

Expected: PASS, with Redis failures now surfacing as explicit fail-closed behavior instead of being silently downgraded.

- [ ] **Step 5: Commit the login throttling hardening**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/service/LoginRateLimitService.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/config/LoginRateLimitProperties.java \
  backend/community-app/src/main/resources/application.yml \
  backend/community-app/src/test/java/com/nowcoder/community/auth/service/LoginRateLimitServiceTest.java
git commit -m "fix: make login throttling fail closed on dependency errors"
```

### Task 3: Frontend Local Endpoint Resolution Alignment

**Files:**
- Modify: `frontend/src/config/endpointResolution.js`
- Test: `frontend/src/api/http.resolution.test.js`
- Test: `frontend/src/api/imCoreHttp.test.js`
- Test: `frontend/src/im/imRealtimeClient.test.js`

- [ ] **Step 1: Write the failing frontend tests for documented local gateway inference**

```js
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

describe('http base URL resolution', () => {
  beforeEach(() => {
    vi.resetModules()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
    try {
      delete globalThis.__COMMUNITY_RUNTIME_CONFIG__
    } catch {}
  })

  it('should infer the local gateway API base URL from localhost preview ports', async () => {
    vi.stubGlobal('location', {
      protocol: 'http:',
      hostname: 'localhost',
      host: 'localhost:12881',
      port: '12881',
      href: 'http://localhost:12881/'
    })

    const { default: http } = await import('./http')

    expect(http.defaults.baseURL).toBe('http://localhost:12880')
  })
})
```

```js
it('should infer the local gateway IM HTTP base URL from localhost preview ports', async () => {
  vi.stubGlobal('location', {
    protocol: 'http:',
    hostname: '127.0.0.1',
    host: '127.0.0.1:5173',
    port: '5173',
    href: 'http://127.0.0.1:5173/'
  })

  const { default: imCoreHttp } = await import('./imCoreHttp')

  expect(imCoreHttp.defaults.baseURL).toBe('http://127.0.0.1:12880')
})
```

```js
it('should infer the local gateway websocket URL from localhost preview ports', async () => {
  vi.stubGlobal('location', {
    protocol: 'http:',
    hostname: 'localhost',
    host: 'localhost:12881',
    port: '12881',
    href: 'http://localhost:12881/'
  })

  const { imRealtimeClient } = await import('./imRealtimeClient')
  imRealtimeClient.connect('token-1')

  expect(FakeWebSocket.instances[0].url).toBe('ws://localhost:12880/ws/im')
})
```

- [ ] **Step 2: Run the focused frontend tests to verify they fail**

Run: `npm test -- src/api/http.resolution.test.js src/api/imCoreHttp.test.js src/im/imRealtimeClient.test.js`

Expected: FAIL because current API / IM HTTP fallbacks return `''` and current websocket fallback points to the current page port instead of `12880`.

- [ ] **Step 3: Write the minimal centralized endpoint inference helper**

```js
import { readRuntimeConfigString } from './runtimeConfig'

const LOCAL_GATEWAY_SOURCE_PORTS = new Set(['5173', '12881', '12890', '12888'])

function readViteString(name) {
  const value = import.meta.env?.[name]
  return typeof value === 'string' && value.trim() ? value.trim() : ''
}

function inferLocalGatewayOrigin() {
  try {
    const loc = globalThis?.location
    if (!loc) return ''
    const host = String(loc.hostname || '').trim()
    const port = String(loc.port || '').trim()
    if (!host || !LOCAL_GATEWAY_SOURCE_PORTS.has(port)) return ''
    if (host !== 'localhost' && host !== '127.0.0.1') return ''
    return `${loc.protocol}//${host}:12880`
  } catch {
    return ''
  }
}

export function resolveApiBaseUrl() {
  return readRuntimeConfigString('apiBaseUrl') || readViteString('VITE_API_BASE_URL') || inferLocalGatewayOrigin() || ''
}

export function resolveImHttpBaseUrl() {
  return readRuntimeConfigString('imHttpBaseUrl') || readViteString('VITE_IM_CORE_BASE_URL') || inferLocalGatewayOrigin() || ''
}

export function resolveImWsUrl() {
  const configured = readRuntimeConfigString('imWsUrl') || readViteString('VITE_IM_WS_URL')
  if (configured) return configured

  const gatewayOrigin = inferLocalGatewayOrigin()
  if (gatewayOrigin) {
    return gatewayOrigin.replace(/^http/, 'ws') + '/ws/im'
  }

  try {
    const loc = globalThis?.location
    if (!loc) return ''
    const scheme = loc.protocol === 'https:' ? 'wss' : 'ws'
    return `${scheme}://${loc.host}/ws/im`
  } catch {
    return ''
  }
}
```

- [ ] **Step 4: Run the focused frontend tests again to verify they pass**

Run: `npm test -- src/api/http.resolution.test.js src/api/imCoreHttp.test.js src/im/imRealtimeClient.test.js`

Expected: PASS, with all three clients now resolving to `:12880` for documented local development ports.

- [ ] **Step 5: Commit the frontend endpoint fix**

```bash
git add frontend/src/config/endpointResolution.js \
  frontend/src/api/http.resolution.test.js \
  frontend/src/api/imCoreHttp.test.js \
  frontend/src/im/imRealtimeClient.test.js
git commit -m "fix: align local frontend endpoint resolution with gateway topology"
```

### Task 4: Gateway Shared Redis Rate Limiting

**Files:**
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/RateLimiter.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/RedisRateLimiter.java`
- Create: `backend/community-gateway/src/main/resources/application-redis-cluster.yml`
- Modify: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/InMemoryRateLimiter.java`
- Modify: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/EdgeConfig.java`
- Modify: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/RateLimitWebFilter.java`
- Modify: `backend/community-gateway/src/main/resources/application.yml`
- Modify: `backend/community-gateway/pom.xml`
- Modify: `deploy/compose.runtime.services.single.yml`
- Modify: `deploy/compose.runtime.services.cluster.yml`
- Test: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/RateLimitWebFilterTest.java`
- Test: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/RedisRateLimiterTest.java`

- [ ] **Step 1: Write the failing tests for the new limiter abstraction and Redis implementation**

```java
package com.nowcoder.community.gateway.edge;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitWebFilterTest {

    @Test
    void shouldBlockWhenSharedLimiterRejectsTheRequest() {
        RateLimitProperties properties = new RateLimitProperties();
        RateLimitProperties.Policy policy = new RateLimitProperties.Policy();
        policy.setLimit(1);
        policy.setWindow(Duration.ofSeconds(30));
        properties.getPolicies().put("/limited", policy);

        RateLimiter limiter = mock(RateLimiter.class);
        when(limiter.allow("ip:10.10.10.10:/limited", policy)).thenReturn(false);

        RateLimitWebFilter filter = new RateLimitWebFilter(properties, limiter);
        AtomicInteger chainInvocations = new AtomicInteger();
        WebFilterChain chain = exchange -> {
            chainInvocations.incrementAndGet();
            return Mono.empty();
        };

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/limited").remoteAddress(new InetSocketAddress("10.10.10.10", 9090)).build()
        );

        filter.filter(exchange, chain).block();

        assertThat(chainInvocations).hasValue(0);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
```

```java
package com.nowcoder.community.gateway.edge;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRateLimiterTest {

    @Test
    void shouldExpireTheWindowWhenFirstIncrementSucceeds() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("gateway:rate-limit:principal:alice:/limited")).thenReturn(1L);

        RateLimitProperties.Policy policy = new RateLimitProperties.Policy();
        policy.setLimit(2);
        policy.setWindow(Duration.ofSeconds(30));

        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate);

        assertThat(limiter.allow("principal:alice:/limited", policy)).isTrue();
        verify(redisTemplate).expire("gateway:rate-limit:principal:alice:/limited", Duration.ofSeconds(30));
    }
}
```

- [ ] **Step 2: Run the gateway tests to verify they fail**

Run: `mvn -pl :community-gateway -Dtest=RateLimitWebFilterTest,RedisRateLimiterTest test`

Expected: FAIL because `RateLimiter` and `RedisRateLimiter` do not exist yet, and `RateLimitWebFilter` still depends on `InMemoryRateLimiter`.

- [ ] **Step 3: Write the minimal shared Redis rate limiting implementation**

```java
package com.nowcoder.community.gateway.edge;

public interface RateLimiter {
    boolean allow(String key, RateLimitProperties.Policy policy);
}
```

```java
package com.nowcoder.community.gateway.edge;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

public class RedisRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "gateway:rate-limit:";

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean allow(String key, RateLimitProperties.Policy policy) {
        int limit = Math.max(1, policy.getLimit());
        Duration window = policy.getWindow() == null ? Duration.ofMinutes(1) : policy.getWindow();
        String redisKey = KEY_PREFIX + key;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count == null) {
            throw new IllegalStateException("redis increment returned null");
        }
        if (count == 1L) {
            redisTemplate.expire(redisKey, window);
        }
        return count <= limit;
    }
}
```

```java
package com.nowcoder.community.gateway.edge;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    @Override
    public boolean allow(String key, RateLimitProperties.Policy policy) {
        int limit = Math.max(1, policy.getLimit());
        Duration window = policy.getWindow() == null ? Duration.ofMinutes(1) : policy.getWindow();
        long windowMillis = Math.max(1L, window.toMillis());
        long now = System.currentTimeMillis();
        Counter counter = counters.compute(key, (ignored, existing) ->
                existing == null || now >= existing.windowEndEpochMillis ? new Counter(now + windowMillis) : existing
        );
        return counter.incrementAndGet() <= limit;
    }

    private static final class Counter {
        private final long windowEndEpochMillis;
        private final AtomicInteger value = new AtomicInteger(0);

        private Counter(long windowEndEpochMillis) {
            this.windowEndEpochMillis = windowEndEpochMillis;
        }

        private int incrementAndGet() {
            return value.incrementAndGet();
        }
    }
}
```

```java
package com.nowcoder.community.gateway.edge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RateLimitProperties.class, TrafficPolicyProperties.class})
public class EdgeConfig {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    RateLimiter redisRateLimiter(StringRedisTemplate redisTemplate) {
        return new RedisRateLimiter(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    RateLimiter inMemoryRateLimiter() {
        return new InMemoryRateLimiter();
    }

    @Bean
    RateLimitWebFilter rateLimitWebFilter(RateLimitProperties properties, RateLimiter limiter) {
        return new RateLimitWebFilter(properties, limiter);
    }
}
```

```java
package com.nowcoder.community.gateway.edge;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class RateLimitWebFilter implements WebFilter {

    private final RateLimitProperties properties;
    private final RateLimiter limiter;

    public RateLimitWebFilter(RateLimitProperties properties, RateLimiter limiter) {
        this.properties = properties;
        this.limiter = limiter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange == null || chain == null) {
            return Mono.empty();
        }
        String path = exchange.getRequest().getPath().value();
        RateLimitProperties.Policy policy = properties == null ? null : properties.getPolicies().get(path);
        if (properties == null || !properties.isEnabled() || policy == null || !policy.isEnabled()) {
            return chain.filter(exchange);
        }
        return exchange.getPrincipal()
                .map(principal -> principal == null ? "" : principal.getName())
                .filter(StringUtils::hasText)
                .map(name -> "principal:" + name + ":" + path)
                .switchIfEmpty(Mono.just(remoteAddressKey(exchange, path)))
                .flatMap(key -> applyPolicy(exchange, chain, key, policy));
    }

    private static String remoteAddressKey(ServerWebExchange exchange, String path) {
        if (exchange == null || exchange.getRequest() == null || exchange.getRequest().getRemoteAddress() == null) {
            return "ip:unknown:" + path;
        }
        String host = exchange.getRequest().getRemoteAddress().getAddress() == null
                ? exchange.getRequest().getRemoteAddress().getHostString()
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        return "ip:" + (StringUtils.hasText(host) ? host : "unknown") + ":" + path;
    }
}
```

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

```yaml
# backend/community-gateway/src/main/resources/application.yml
spring:
  application:
    name: community-gateway
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:127.0.0.1}
      port: ${SPRING_DATA_REDIS_PORT:6379}
```

```yaml
# backend/community-gateway/src/main/resources/application-redis-cluster.yml
spring:
  config:
    activate:
      on-profile: redis-cluster
  data:
    redis:
      cluster:
        nodes: ${SPRING_DATA_REDIS_CLUSTER_NODES:127.0.0.1:6379}
```

```yaml
# deploy/compose.runtime.services.single.yml (gateway env block)
- SPRING_DATA_REDIS_HOST=${SPRING_DATA_REDIS_HOST:-redis}
```

```yaml
# deploy/compose.runtime.services.cluster.yml (gateway replica env blocks)
- SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-dev},redis-cluster,volume-log-export
- SPRING_DATA_REDIS_CLUSTER_NODES=${SPRING_DATA_REDIS_CLUSTER_NODES:-redis-1:6379,redis-2:6379,redis-3:6379,redis-4:6379,redis-5:6379,redis-6:6379}
```

- [ ] **Step 4: Run the gateway tests again to verify they pass**

Run: `mvn -pl :community-gateway -Dtest=RateLimitWebFilterTest,RedisRateLimiterTest test`

Expected: PASS, with the web filter now testing the abstraction and the Redis limiter verifying first-write TTL behavior.

- [ ] **Step 5: Commit the gateway rate limiting hardening**

```bash
git add backend/community-gateway/pom.xml \
  backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/RateLimiter.java \
  backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/RedisRateLimiter.java \
  backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/InMemoryRateLimiter.java \
  backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/EdgeConfig.java \
  backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/RateLimitWebFilter.java \
  backend/community-gateway/src/main/resources/application.yml \
  backend/community-gateway/src/main/resources/application-redis-cluster.yml \
  backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/RateLimitWebFilterTest.java \
  backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/RedisRateLimiterTest.java \
  deploy/compose.runtime.services.single.yml \
  deploy/compose.runtime.services.cluster.yml
git commit -m "fix: move gateway rate limiting to shared redis state"
```

### Task 5: Final Focused Verification

**Files:**
- Verify only: `backend/community-app`
- Verify only: `backend/community-gateway`
- Verify only: `frontend`

- [ ] **Step 1: Run the complete focused backend regression set**

Run: `mvn -pl :community-app -Dtest=ReindexJobServiceTest,SearchReindexExecutionServiceTest,SearchAdminServiceTest,LoginRateLimitServiceTest test`

Expected: PASS with all search and auth hardening regressions green.

- [ ] **Step 2: Run the complete focused gateway regression set**

Run: `mvn -pl :community-gateway -Dtest=RateLimitWebFilterTest,RedisRateLimiterTest test`

Expected: PASS with both the abstraction-level filter test and Redis limiter unit tests green.

- [ ] **Step 3: Run the complete focused frontend regression set**

Run: `npm test -- src/api/http.resolution.test.js src/api/imCoreHttp.test.js src/im/imRealtimeClient.test.js`

Expected: PASS with local endpoint inference aligned across API, IM HTTP, and IM websocket clients.

- [ ] **Step 4: Run a final workspace diff check**

Run: `git status --short`

Expected: only the intended tracked changes remain; no accidental unrelated edits appear.
