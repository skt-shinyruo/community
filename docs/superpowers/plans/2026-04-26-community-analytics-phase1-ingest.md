# Community Analytics Phase 1 Ingest Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Phase 1 analytics ingest loop so real backend requests and successful logins write UV / DAU into Redis without affecting business requests.

**Architecture:** Add a small analytics ingest layer under `backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/`. A request filter classifies allowed API traffic, resolves client IP and authenticated user UUID, then calls an ingest service that writes UV and DAU through the existing `AnalyticsService`. Because the current JWT subject is a UUID while the existing DAU bitmap requires an integer bit offset, add a Redis-backed UUID-to-int ordinal repository for analytics-only DAU offsets.

**Tech Stack:** Spring Boot 3, Servlet `OncePerRequestFilter`, Spring Security `Authentication` / JWT, `StringRedisTemplate`, existing `AnalyticsService`, JUnit 5, AssertJ, Mockito, Maven.

---

## Scope Check

The design spec covers five phases. This plan intentionally implements only Phase 1:

- automatic UV capture from eligible requests
- automatic DAU capture from eligible authenticated requests
- successful-login DAU supplement
- fail-open analytics writes
- conservative ingest configuration

PV, trend APIs, dashboard UI, content metrics, retention, and long-term snapshots require separate plans after Phase 1 is merged.

## File Structure

Create:

- `backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/AnalyticsIngestProperties.java`
  Defines `analytics.ingest.*` configuration with conservative defaults.
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/AnalyticsRequestClassifier.java`
  Applies include/exclude path rules and status/method checks.
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/AnalyticsPrincipalResolver.java`
  Extracts authenticated user UUID from the current `Authentication`.
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/AnalyticsIngestService.java`
  Fail-open service that writes UV and DAU after request/login classification.
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/AnalyticsRequestCaptureFilter.java`
  Servlet filter that runs after the downstream request and records successful eligible traffic.
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/AnalyticsUserOrdinalRepository.java`
  Repository contract for stable analytics-only UUID-to-int DAU offsets.
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/RedisAnalyticsUserOrdinalRepository.java`
  Redis implementation for the ordinal mapping. Its Lua script keys use the shared `{analytics:user-ordinal}` hash tag so map and sequence keys stay in one Redis Cluster slot.

Modify:

- `backend/community-app/src/main/java/com/nowcoder/community/auth/service/AuthService.java`
  Injects `AnalyticsIngestService` and records DAU after successful login.
- `backend/community-app/src/main/resources/application.yml`
  Adds production ingest configuration with `enabled: false`.
- `backend/community-app/src/test/resources/application.yml`
  Adds test ingest configuration with `enabled: true`.
- `backend/community-app/src/test/java/com/nowcoder/community/auth/service/RefreshTokenServiceTest.java`
  Updates direct `AuthService` construction to pass the analytics ingest mock.
- `docs/business-logic/analytics-ingest-flow.md`
  Updates current-state documentation after Phase 1 exists.

Test:

- `backend/community-app/src/test/java/com/nowcoder/community/analytics/ingest/AnalyticsRequestClassifierTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/analytics/ingest/AnalyticsPrincipalResolverTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/analytics/ingest/AnalyticsIngestServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/analytics/ingest/AnalyticsRequestCaptureFilterTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/analytics/repo/RedisAnalyticsUserOrdinalRepositoryTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/auth/service/AuthServiceLoginTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/auth/service/RefreshTokenServiceTest.java`

---

### Task 1: Add Ingest Properties And Request Classifier

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/AnalyticsIngestProperties.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/AnalyticsRequestClassifier.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/analytics/ingest/AnalyticsRequestClassifierTest.java`

- [ ] **Step 1: Write the failing classifier test**

Create `AnalyticsRequestClassifierTest.java`:

```java
package com.nowcoder.community.analytics.ingest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsRequestClassifierTest {

    @Test
    void shouldCaptureIncludedSuccessfulApiRequest() {
        AnalyticsIngestProperties properties = new AnalyticsIngestProperties();
        properties.setEnabled(true);
        properties.setIncludePaths(List.of("/api/posts/**", "/api/search/**"));
        properties.setExcludePaths(List.of("/api/analytics/**", "/api/auth/**"));
        AnalyticsRequestClassifier classifier = new AnalyticsRequestClassifier(properties);

        AnalyticsRequestClassifier.Decision decision = classifier.classify("GET", "/api/posts/123", 200);

        assertThat(decision.capture()).isTrue();
        assertThat(decision.normalizedPath()).isEqualTo("/api/posts/{id}");
    }

    @Test
    void shouldSkipExcludedPathEvenWhenIncludedByApiPrefix() {
        AnalyticsIngestProperties properties = new AnalyticsIngestProperties();
        properties.setEnabled(true);
        properties.setIncludePaths(List.of("/api/**"));
        properties.setExcludePaths(List.of("/api/analytics/**"));
        AnalyticsRequestClassifier classifier = new AnalyticsRequestClassifier(properties);

        AnalyticsRequestClassifier.Decision decision = classifier.classify("GET", "/api/analytics/uv", 200);

        assertThat(decision.capture()).isFalse();
        assertThat(decision.reason()).isEqualTo("excluded_path");
    }

    @Test
    void shouldSkipWhenDisabledOrServerError() {
        AnalyticsIngestProperties properties = new AnalyticsIngestProperties();
        properties.setEnabled(false);
        AnalyticsRequestClassifier classifier = new AnalyticsRequestClassifier(properties);

        assertThat(classifier.classify("GET", "/api/posts/123", 200).capture()).isFalse();

        properties.setEnabled(true);
        properties.setIncludePaths(List.of("/api/posts/**"));
        assertThat(classifier.classify("GET", "/api/posts/123", 500).capture()).isFalse();
    }
}
```

- [ ] **Step 2: Run the classifier test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -pl community-app -am -Dtest=AnalyticsRequestClassifierTest test
```

Expected: compile failure because `AnalyticsIngestProperties` and `AnalyticsRequestClassifier` do not exist.

- [ ] **Step 3: Implement properties and classifier**

Create `AnalyticsIngestProperties.java`:

```java
package com.nowcoder.community.analytics.ingest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "analytics.ingest")
public class AnalyticsIngestProperties {

    private boolean enabled = false;
    private boolean recordUv = true;
    private boolean recordDau = true;
    private List<String> includePaths = new ArrayList<>(List.of("/api/posts/**", "/api/search/**", "/api/messages/**", "/api/notices/**", "/api/im-governance/**"));
    private List<String> excludePaths = new ArrayList<>(List.of("/api/analytics/**", "/api/auth/**", "/api/ops/**", "/actuator/**", "/internal/**", "/files/**"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRecordUv() {
        return recordUv;
    }

    public void setRecordUv(boolean recordUv) {
        this.recordUv = recordUv;
    }

    public boolean isRecordDau() {
        return recordDau;
    }

    public void setRecordDau(boolean recordDau) {
        this.recordDau = recordDau;
    }

    public List<String> getIncludePaths() {
        return includePaths;
    }

    public void setIncludePaths(List<String> includePaths) {
        this.includePaths = includePaths == null ? new ArrayList<>() : new ArrayList<>(includePaths);
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths == null ? new ArrayList<>() : new ArrayList<>(excludePaths);
    }
}
```

Create `AnalyticsRequestClassifier.java`:

```java
package com.nowcoder.community.analytics.ingest;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

@Component
public class AnalyticsRequestClassifier {

    private final AnalyticsIngestProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AnalyticsRequestClassifier(AnalyticsIngestProperties properties) {
        this.properties = properties;
    }

    public Decision classify(String method, String path, int status) {
        if (properties == null || !properties.isEnabled()) {
            return Decision.skip("disabled");
        }
        if (!StringUtils.hasText(method) || !StringUtils.hasText(path)) {
            return Decision.skip("missing_request");
        }
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return Decision.skip("options");
        }
        if (status >= 500) {
            return Decision.skip("server_error");
        }
        if (matchesAny(path, properties.getExcludePaths())) {
            return Decision.skip("excluded_path");
        }
        if (!matchesAny(path, properties.getIncludePaths())) {
            return Decision.skip("not_included");
        }
        return new Decision(true, normalizePath(path), "captured");
    }

    private boolean matchesAny(String path, Iterable<String> patterns) {
        if (patterns == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (StringUtils.hasText(pattern) && pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private String normalizePath(String path) {
        return path.replaceAll("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?=/|$)", "/{uuid}")
                .replaceAll("/\\d+(?=/|$)", "/{id}");
    }

    public record Decision(boolean capture, String normalizedPath, String reason) {
        static Decision skip(String reason) {
            return new Decision(false, null, reason);
        }
    }
}
```

- [ ] **Step 4: Run the classifier test to verify it passes**

Run:

```bash
mvn -f backend/pom.xml -pl community-app -am -Dtest=AnalyticsRequestClassifierTest test
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit Task 1**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/AnalyticsIngestProperties.java \
        backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/AnalyticsRequestClassifier.java \
        backend/community-app/src/test/java/com/nowcoder/community/analytics/ingest/AnalyticsRequestClassifierTest.java
git commit -m "feat: classify analytics ingest requests"
```

---

### Task 2: Add Principal Resolver And Redis User Ordinal Mapping

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/AnalyticsPrincipalResolver.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/AnalyticsUserOrdinalRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/RedisAnalyticsUserOrdinalRepository.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/analytics/ingest/AnalyticsPrincipalResolverTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/analytics/repo/RedisAnalyticsUserOrdinalRepositoryTest.java`

- [ ] **Step 1: Write failing resolver and ordinal tests**

Create `AnalyticsPrincipalResolverTest.java`:

```java
package com.nowcoder.community.analytics.ingest;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsPrincipalResolverTest {

    @Test
    void shouldResolveUuidSubjectFromJwtPrincipal() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T01:00:00Z"))
                .build();
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(jwt, null);
        authentication.setAuthenticated(true);

        assertThat(new AnalyticsPrincipalResolver().resolveUserUuid(authentication)).isEqualTo(userId);
    }

    @Test
    void shouldIgnoreAnonymousOrInvalidPrincipal() {
        AnalyticsPrincipalResolver resolver = new AnalyticsPrincipalResolver();

        assertThat(resolver.resolveUserUuid(null)).isNull();
        assertThat(resolver.resolveUserUuid(new TestingAuthenticationToken("anonymousUser", null))).isNull();
    }
}
```

Create `RedisAnalyticsUserOrdinalRepositoryTest.java`:

```java
package com.nowcoder.community.analytics.repo;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAnalyticsUserOrdinalRepositoryTest {

    @Test
    void shouldResolveStablePositiveOrdinalThroughRedisScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("11111111-1111-1111-1111-111111111111"))).thenReturn(7L);
        RedisAnalyticsUserOrdinalRepository repository = new RedisAnalyticsUserOrdinalRepository(redisTemplate);

        int ordinal = repository.resolveOrdinal(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        assertThat(ordinal).isEqualTo(7);
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), eq("11111111-1111-1111-1111-111111111111"));
        assertThat(keys.getValue()).containsExactly("{analytics:user-ordinal}:map", "{analytics:user-ordinal}:seq");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
mvn -f backend/pom.xml -pl community-app -am -Dtest=AnalyticsPrincipalResolverTest,RedisAnalyticsUserOrdinalRepositoryTest test
```

Expected: compile failure because resolver and ordinal repository do not exist.

- [ ] **Step 3: Implement principal resolver and ordinal repository**

Create `AnalyticsPrincipalResolver.java`:

```java
package com.nowcoder.community.analytics.ingest;

import com.nowcoder.community.infra.security.auth.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AnalyticsPrincipalResolver {

    public UUID resolveUserUuid(Authentication authentication) {
        return CurrentUser.tryUserUuid(authentication);
    }
}
```

Create `AnalyticsUserOrdinalRepository.java`:

```java
package com.nowcoder.community.analytics.repo;

import java.util.UUID;

public interface AnalyticsUserOrdinalRepository {

    int resolveOrdinal(UUID userId);
}
```

Create `RedisAnalyticsUserOrdinalRepository.java`:

```java
package com.nowcoder.community.analytics.repo;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class RedisAnalyticsUserOrdinalRepository implements AnalyticsUserOrdinalRepository {

    private static final String USER_ORDINAL_MAP_KEY = "{analytics:user-ordinal}:map";
    private static final String USER_ORDINAL_SEQ_KEY = "{analytics:user-ordinal}:seq";
    private static final DefaultRedisScript<Long> RESOLVE_ORDINAL_SCRIPT = new DefaultRedisScript<>();

    static {
        RESOLVE_ORDINAL_SCRIPT.setResultType(Long.class);
        RESOLVE_ORDINAL_SCRIPT.setScriptText(
                "local existing = redis.call('hget', KEYS[1], ARGV[1]) " +
                        "if existing then return tonumber(existing) end " +
                        "local next = redis.call('incr', KEYS[2]) " +
                        "redis.call('hset', KEYS[1], ARGV[1], next) " +
                        "return next"
        );
    }

    private final StringRedisTemplate redisTemplate;

    public RedisAnalyticsUserOrdinalRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public int resolveOrdinal(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        Long value = redisTemplate.execute(
                RESOLVE_ORDINAL_SCRIPT,
                List.of(USER_ORDINAL_MAP_KEY, USER_ORDINAL_SEQ_KEY),
                userId.toString()
        );
        if (value == null || value <= 0 || value > Integer.MAX_VALUE) {
            throw new IllegalStateException("invalid analytics user ordinal: " + value);
        }
        return value.intValue();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
mvn -f backend/pom.xml -pl community-app -am -Dtest=AnalyticsPrincipalResolverTest,RedisAnalyticsUserOrdinalRepositoryTest test
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit Task 2**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/AnalyticsPrincipalResolver.java \
        backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/AnalyticsUserOrdinalRepository.java \
        backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/RedisAnalyticsUserOrdinalRepository.java \
        backend/community-app/src/test/java/com/nowcoder/community/analytics/ingest/AnalyticsPrincipalResolverTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/analytics/repo/RedisAnalyticsUserOrdinalRepositoryTest.java
git commit -m "feat: resolve analytics user ordinals"
```

---

### Task 3: Add Fail-Open Analytics Ingest Service

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/AnalyticsIngestService.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/analytics/ingest/AnalyticsIngestServiceTest.java`

- [ ] **Step 1: Write the failing ingest service test**

Create `AnalyticsIngestServiceTest.java`:

```java
package com.nowcoder.community.analytics.ingest;

import com.nowcoder.community.analytics.repo.AnalyticsUserOrdinalRepository;
import com.nowcoder.community.analytics.service.AnalyticsService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalyticsIngestServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-04-26T01:02:03Z"), ZoneOffset.UTC);

    @Test
    void shouldRecordUvAndDauForRequest() {
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        AnalyticsUserOrdinalRepository ordinalRepository = mock(AnalyticsUserOrdinalRepository.class);
        when(ordinalRepository.resolveOrdinal(UUID.fromString("11111111-1111-1111-1111-111111111111"))).thenReturn(9);
        AnalyticsIngestService service = new AnalyticsIngestService(analyticsService, ordinalRepository, enabledProperties(), clock);

        service.recordRequest("1.1.1.1", UUID.fromString("11111111-1111-1111-1111-111111111111"));

        verify(analyticsService).recordUv(LocalDate.of(2026, 4, 26), "1.1.1.1");
        verify(analyticsService).recordDau(LocalDate.of(2026, 4, 26), 9);
    }

    @Test
    void shouldFailOpenWhenAnalyticsWriteThrows() {
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        AnalyticsUserOrdinalRepository ordinalRepository = mock(AnalyticsUserOrdinalRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(ordinalRepository.resolveOrdinal(userId)).thenReturn(9);
        doThrow(new RuntimeException("redis down")).when(analyticsService).recordUv(LocalDate.of(2026, 4, 26), "1.1.1.1");
        AnalyticsIngestService service = new AnalyticsIngestService(analyticsService, ordinalRepository, enabledProperties(), clock);

        service.recordRequest("1.1.1.1", userId);

        verify(analyticsService).recordDau(LocalDate.of(2026, 4, 26), 9);
    }

    @Test
    void shouldFailOpenWhenOrdinalResolutionThrows() {
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        AnalyticsUserOrdinalRepository ordinalRepository = mock(AnalyticsUserOrdinalRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        doThrow(new RuntimeException("redis down")).when(ordinalRepository).resolveOrdinal(userId);
        AnalyticsIngestService service = new AnalyticsIngestService(analyticsService, ordinalRepository, enabledProperties(), clock);

        service.recordRequest("1.1.1.1", userId);

        verify(analyticsService).recordUv(LocalDate.of(2026, 4, 26), "1.1.1.1");
    }

    @Test
    void shouldFailOpenWhenDauWriteThrows() {
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        AnalyticsUserOrdinalRepository ordinalRepository = mock(AnalyticsUserOrdinalRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(ordinalRepository.resolveOrdinal(userId)).thenReturn(9);
        doThrow(new RuntimeException("redis down")).when(analyticsService).recordDau(LocalDate.of(2026, 4, 26), 9);
        AnalyticsIngestService service = new AnalyticsIngestService(analyticsService, ordinalRepository, enabledProperties(), clock);

        service.recordRequest("1.1.1.1", userId);

        verify(analyticsService).recordUv(LocalDate.of(2026, 4, 26), "1.1.1.1");
    }

    @Test
    void shouldSkipWhenDisabled() {
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        AnalyticsUserOrdinalRepository ordinalRepository = mock(AnalyticsUserOrdinalRepository.class);
        AnalyticsIngestProperties properties = new AnalyticsIngestProperties();
        properties.setEnabled(false);
        AnalyticsIngestService service = new AnalyticsIngestService(analyticsService, ordinalRepository, properties, clock);

        service.recordRequest("1.1.1.1", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        service.recordLoginSuccess(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        verifyNoInteractions(analyticsService, ordinalRepository);
    }

    @Test
    void shouldRespectUvAndDauRecordFlags() {
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        AnalyticsUserOrdinalRepository ordinalRepository = mock(AnalyticsUserOrdinalRepository.class);
        AnalyticsIngestProperties properties = enabledProperties();
        properties.setRecordUv(false);
        properties.setRecordDau(false);
        AnalyticsIngestService service = new AnalyticsIngestService(analyticsService, ordinalRepository, properties, clock);

        service.recordRequest("1.1.1.1", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        service.recordLoginSuccess(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        verifyNoInteractions(analyticsService, ordinalRepository);
    }

    private AnalyticsIngestProperties enabledProperties() {
        AnalyticsIngestProperties properties = new AnalyticsIngestProperties();
        properties.setEnabled(true);
        return properties;
    }
}
```

- [ ] **Step 2: Run the ingest service test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -pl community-app -am -Dtest=AnalyticsIngestServiceTest test
```

Expected: compile failure because `AnalyticsIngestService` does not exist.

- [ ] **Step 3: Implement ingest service**

Create `AnalyticsIngestService.java`:

```java
package com.nowcoder.community.analytics.ingest;

import com.nowcoder.community.analytics.repo.AnalyticsUserOrdinalRepository;
import com.nowcoder.community.analytics.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AnalyticsIngestService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsIngestService.class);

    private final AnalyticsService analyticsService;
    private final AnalyticsUserOrdinalRepository ordinalRepository;
    private final AnalyticsIngestProperties properties;
    private final Clock clock;
    private final AtomicLong uvFailureCount = new AtomicLong();
    private final AtomicLong dauFailureCount = new AtomicLong();

    @Autowired
    public AnalyticsIngestService(
            AnalyticsService analyticsService,
            AnalyticsUserOrdinalRepository ordinalRepository,
            AnalyticsIngestProperties properties
    ) {
        this(analyticsService, ordinalRepository, properties, Clock.systemDefaultZone());
    }

    AnalyticsIngestService(
            AnalyticsService analyticsService,
            AnalyticsUserOrdinalRepository ordinalRepository,
            AnalyticsIngestProperties properties,
            Clock clock
    ) {
        this.analyticsService = analyticsService;
        this.ordinalRepository = ordinalRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public void recordRequest(String ip, UUID userId) {
        if (properties == null || !properties.isEnabled()) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        if (properties.isRecordUv()) {
            recordUv(today, ip);
        }
        if (properties.isRecordDau()) {
            recordDau(today, userId);
        }
    }

    public void recordLoginSuccess(UUID userId) {
        if (properties == null || !properties.isEnabled() || !properties.isRecordDau()) {
            return;
        }
        recordDau(LocalDate.now(clock), userId);
    }

    private void recordUv(LocalDate date, String ip) {
        if (!StringUtils.hasText(ip)) {
            return;
        }
        try {
            analyticsService.recordUv(date, ip);
        } catch (RuntimeException e) {
            logFailure("UV", date, uvFailureCount, e);
        }
    }

    private void recordDau(LocalDate date, UUID userId) {
        if (userId == null) {
            return;
        }
        try {
            int ordinal = ordinalRepository.resolveOrdinal(userId);
            analyticsService.recordDau(date, ordinal);
        } catch (RuntimeException e) {
            logFailure("DAU", date, dauFailureCount, e);
        }
    }

    private void logFailure(String metric, LocalDate date, AtomicLong failureCount, RuntimeException e) {
        long count = failureCount.incrementAndGet();
        if (count <= 3 || isPowerOfTwo(count)) {
            log.warn("[analytics][ingest] record {} failed: date={}, failures={}, error={}", metric, date, count, e.toString());
        }
        if (log.isDebugEnabled()) {
            log.debug("[analytics][ingest] record {} failed: date={}, failures={}", metric, date, count, e);
        }
    }

    private boolean isPowerOfTwo(long value) {
        return value > 0 && (value & (value - 1)) == 0;
    }
}
```

- [ ] **Step 4: Run the ingest service test to verify it passes**

Run:

```bash
mvn -f backend/pom.xml -pl community-app -am -Dtest=AnalyticsIngestServiceTest test
```

Expected: `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit Task 3**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/AnalyticsIngestService.java \
        backend/community-app/src/test/java/com/nowcoder/community/analytics/ingest/AnalyticsIngestServiceTest.java
git commit -m "feat: add analytics ingest service"
```

---

### Task 4: Add Request Capture Filter

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/AnalyticsRequestCaptureFilter.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/analytics/ingest/AnalyticsRequestCaptureFilterTest.java`

- [ ] **Step 1: Write the failing filter test**

Create `AnalyticsRequestCaptureFilterTest.java`:

```java
package com.nowcoder.community.analytics.ingest;

import com.nowcoder.community.common.web.net.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalyticsRequestCaptureFilterTest {

    @Test
    void shouldRecordEligibleRequestAfterChain() throws Exception {
        AnalyticsRequestClassifier classifier = mock(AnalyticsRequestClassifier.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AnalyticsPrincipalResolver principalResolver = mock(AnalyticsPrincipalResolver.class);
        AnalyticsIngestService ingestService = mock(AnalyticsIngestService.class);
        AnalyticsRequestCaptureFilter filter = new AnalyticsRequestCaptureFilter(classifier, clientIpResolver, principalResolver, ingestService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(200);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(classifier.classify("GET", "/api/posts/123", 200))
                .thenReturn(new AnalyticsRequestClassifier.Decision(true, "/api/posts/{id}", "captured"));
        when(clientIpResolver.resolve(request)).thenReturn(new ClientIpResolver.ResolvedClientIp("1.1.1.1", ClientIpResolver.SOURCE_REMOTE));
        when(principalResolver.resolveUserUuid(null)).thenReturn(userId);

        filter.doFilter(request, response, chain);

        verify(ingestService).recordRequest("1.1.1.1", userId);
    }

    @Test
    void shouldSkipWhenClassifierSkips() throws Exception {
        AnalyticsRequestClassifier classifier = mock(AnalyticsRequestClassifier.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AnalyticsPrincipalResolver principalResolver = mock(AnalyticsPrincipalResolver.class);
        AnalyticsIngestService ingestService = mock(AnalyticsIngestService.class);
        AnalyticsRequestCaptureFilter filter = new AnalyticsRequestCaptureFilter(classifier, clientIpResolver, principalResolver, ingestService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/analytics/uv");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(classifier.classify("GET", "/api/analytics/uv", 200))
                .thenReturn(new AnalyticsRequestClassifier.Decision(false, null, "excluded_path"));

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));

        verifyNoInteractions(ingestService);
    }

    @Test
    void shouldFailOpenWhenAnalyticsCaptureThrows() throws Exception {
        AnalyticsRequestClassifier classifier = mock(AnalyticsRequestClassifier.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AnalyticsPrincipalResolver principalResolver = mock(AnalyticsPrincipalResolver.class);
        AnalyticsIngestService ingestService = mock(AnalyticsIngestService.class);
        AnalyticsRequestCaptureFilter filter = new AnalyticsRequestCaptureFilter(classifier, clientIpResolver, principalResolver, ingestService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        doThrow(new RuntimeException("classifier failed")).when(classifier).classify("GET", "/api/posts/123", 200);

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));

        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(ingestService);
    }

    @Test
    void shouldNotRecordWhenDownstreamRequestThrows() {
        AnalyticsRequestClassifier classifier = mock(AnalyticsRequestClassifier.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AnalyticsPrincipalResolver principalResolver = mock(AnalyticsPrincipalResolver.class);
        AnalyticsIngestService ingestService = mock(AnalyticsIngestService.class);
        AnalyticsRequestCaptureFilter filter = new AnalyticsRequestCaptureFilter(classifier, clientIpResolver, principalResolver, ingestService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts/123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new ServletException("downstream failed");
        })).isInstanceOf(ServletException.class);

        verifyNoInteractions(classifier, clientIpResolver, principalResolver, ingestService);
    }
}
```

- [ ] **Step 2: Run the filter test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -pl community-app -am -Dtest=AnalyticsRequestCaptureFilterTest test
```

Expected: compile failure because `AnalyticsRequestCaptureFilter` does not exist.

- [ ] **Step 3: Implement request capture filter**

Create `AnalyticsRequestCaptureFilter.java`:

```java
package com.nowcoder.community.analytics.ingest;

import com.nowcoder.community.common.web.net.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class AnalyticsRequestCaptureFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRequestCaptureFilter.class);
    private final AtomicLong captureFailureCount = new AtomicLong();

    private final AnalyticsRequestClassifier classifier;
    private final ClientIpResolver clientIpResolver;
    private final AnalyticsPrincipalResolver principalResolver;
    private final AnalyticsIngestService ingestService;

    public AnalyticsRequestCaptureFilter(
            AnalyticsRequestClassifier classifier,
            ClientIpResolver clientIpResolver,
            AnalyticsPrincipalResolver principalResolver,
            AnalyticsIngestService ingestService
    ) {
        this.classifier = classifier;
        this.clientIpResolver = clientIpResolver;
        this.principalResolver = principalResolver;
        this.ingestService = ingestService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean chainCompleted = false;
        try {
            filterChain.doFilter(request, response);
            chainCompleted = true;
        } finally {
            if (chainCompleted) {
                recordSafely(request, response);
            }
        }
    }

    private void recordSafely(HttpServletRequest request, HttpServletResponse response) {
        try {
            recordIfEligible(request, response);
        } catch (RuntimeException e) {
            logCaptureFailure(request, response, e);
        }
    }

    private void logCaptureFailure(HttpServletRequest request, HttpServletResponse response, RuntimeException e) {
        long count = captureFailureCount.incrementAndGet();
        if (count <= 3 || (count & (count - 1)) == 0) {
            log.warn("[analytics][ingest] request capture failed: method={}, status={}, count={}, error={}",
                    request == null ? null : request.getMethod(),
                    response == null ? null : response.getStatus(),
                    count,
                    e.toString());
        }
        if (log.isDebugEnabled()) {
            log.debug("[analytics][ingest] request capture failed with stack trace", e);
        }
    }

    private void recordIfEligible(HttpServletRequest request, HttpServletResponse response) {
        AnalyticsRequestClassifier.Decision decision = classifier.classify(
                request == null ? null : request.getMethod(),
                request == null ? null : request.getRequestURI(),
                response == null ? 0 : response.getStatus()
        );
        if (decision == null || !decision.capture()) {
            return;
        }
        ClientIpResolver.ResolvedClientIp resolved = clientIpResolver.resolve(request);
        String ip = resolved == null ? null : resolved.ip();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = principalResolver.resolveUserUuid(authentication);
        ingestService.recordRequest(ip, userId);
    }
}
```

- [ ] **Step 4: Run the filter test to verify it passes**

Run:

```bash
mvn -f backend/pom.xml -pl community-app -am -Dtest=AnalyticsRequestCaptureFilterTest test
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit Task 4**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest/AnalyticsRequestCaptureFilter.java \
        backend/community-app/src/test/java/com/nowcoder/community/analytics/ingest/AnalyticsRequestCaptureFilterTest.java
git commit -m "feat: capture analytics request traffic"
```

---

### Task 5: Add Successful Login DAU Supplement

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/AuthService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/AuthServiceLoginTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/RefreshTokenServiceTest.java`

- [ ] **Step 1: Write or update failing login test**

In `AuthServiceLoginTest.java`, add imports:

```java
import com.nowcoder.community.analytics.ingest.AnalyticsIngestService;

import static org.mockito.ArgumentMatchers.anyList;
```

Add the mock field beside the other service mocks:

```java
private final AnalyticsIngestService analyticsIngestService = mock(AnalyticsIngestService.class);
```

Pass it to the `AuthService` constructor in `setUp()`:

```java
authService = new AuthService(
        userCredentialQueryApi,
        jwtTokenService,
        refreshTokenService,
        loginRateLimitService,
        captchaService,
        clientIpResolver,
        analyticsIngestService
);
```

Update `authServiceShouldOnlyExposeUserApiConstructor()` so the constructor assertion includes the new parameter:

```java
assertThat(AuthService.class.getDeclaredConstructors())
        .singleElement()
        .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                UserCredentialQueryApi.class,
                JwtTokenService.class,
                RefreshTokenService.class,
                LoginRateLimitService.class,
                CaptchaService.class,
                ClientIpResolver.class,
                AnalyticsIngestService.class
        ));
```

Add a success-path analytics test:

```java
@Test
void loginShouldRecordDauSupplementAfterSuccessfulAuthentication() {
    UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UserCredentialView user = new UserCredentialView(userId, "alice", 1, 0, null);
    when(userCredentialQueryApi.authenticate("alice", "pw"))
            .thenReturn(UserAuthenticationResultView.authenticated(user));
    when(userCredentialQueryApi.authoritiesOf(user)).thenReturn(List.of("ROLE_USER"));
    when(jwtTokenService.createAccessToken(eq(userId), eq("alice"), anyList())).thenReturn("access-token");
    when(refreshTokenService.issue(userId)).thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", ResponseCookie.from("refresh_token", "refresh-token").build()));
    when(clientIpResolver.resolve(any())).thenReturn(new ClientIpResolver.ResolvedClientIp("1.1.1.1", ClientIpResolver.SOURCE_REMOTE));

    authService.login("alice", "pw", null, null, new MockHttpServletRequest());

    verify(analyticsIngestService).recordLoginSuccess(userId);
}
```

- [ ] **Step 2: Run the login test to verify it fails**

Run:

```bash
mvn -f backend/pom.xml -pl community-app -am -Dtest=AuthServiceLoginTest test
```

Expected: compile failure or verification failure because `AuthService` does not inject/call `AnalyticsIngestService`.

- [ ] **Step 3: Modify AuthService constructor and login success path**

In `AuthService.java`, add import:

```java
import com.nowcoder.community.analytics.ingest.AnalyticsIngestService;
```

Add field:

```java
private final AnalyticsIngestService analyticsIngestService;
```

Add constructor parameter after `ClientIpResolver clientIpResolver`:

```java
AnalyticsIngestService analyticsIngestService
```

Assign it:

```java
this.analyticsIngestService = analyticsIngestService;
```

After successful `SecurityEventLogger.info(...)` and before `return loginResult;`, add:

```java
analyticsIngestService.recordLoginSuccess(user.userId());
```

- [ ] **Step 4: Update all direct AuthService constructor calls in tests**

Search:

```bash
rg -n "new AuthService\\(" backend/community-app/src/test/java
```

For each direct constructor call, pass a mock `AnalyticsIngestService`. The known paths are:

- `backend/community-app/src/test/java/com/nowcoder/community/auth/service/AuthServiceLoginTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/auth/service/RefreshTokenServiceTest.java`

In `RefreshTokenServiceTest.java`, add:

```java
mock(AnalyticsIngestService.class)
```

as the final constructor argument.

- [ ] **Step 5: Run the login test to verify it passes**

Run:

```bash
mvn -f backend/pom.xml -pl community-app -am -Dtest=AuthServiceLoginTest,RefreshTokenServiceTest test
```

Expected: `AuthServiceLoginTest` and `RefreshTokenServiceTest` pass with no failures.

- [ ] **Step 6: Commit Task 5**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/service/AuthService.java \
        backend/community-app/src/test/java/com/nowcoder/community/auth/service/AuthServiceLoginTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/auth/service/RefreshTokenServiceTest.java
git commit -m "feat: record analytics dau on login"
```

---

### Task 6: Add Configuration And Documentation

**Files:**
- Modify: `backend/community-app/src/main/resources/application.yml`
- Modify: `backend/community-app/src/test/resources/application.yml`
- Modify: `docs/business-logic/analytics-ingest-flow.md`

- [ ] **Step 1: Add configuration**

In `backend/community-app/src/main/resources/application.yml`, extend the existing `analytics:` block:

```yaml
analytics:
  max-days-range: 31
  ingest:
    enabled: false
    record-uv: true
    record-dau: true
    include-paths:
      - /api/posts/**
      - /api/search/**
      - /api/messages/**
      - /api/notices/**
      - /api/im-governance/**
    exclude-paths:
      - /api/analytics/**
      - /api/auth/**
      - /api/ops/**
      - /actuator/**
      - /internal/**
      - /files/**
```

In `backend/community-app/src/test/resources/application.yml`, add:

```yaml
analytics:
  max-days-range: 31
  ingest:
    enabled: true
    record-uv: true
    record-dau: true
    include-paths:
      - /api/posts/**
      - /api/search/**
      - /api/messages/**
      - /api/notices/**
      - /api/im-governance/**
    exclude-paths:
      - /api/analytics/**
      - /api/auth/**
      - /api/ops/**
      - /actuator/**
      - /internal/**
      - /files/**
```

- [ ] **Step 2: Update analytics ingest docs**

In `docs/business-logic/analytics-ingest-flow.md`, replace the “当前没有看到 servlet filter” section with a new current-state description:

```markdown
### 6.2 analytics 自动采集入口

Phase 1 后，analytics 通过 `AnalyticsRequestCaptureFilter` 自动采集被配置允许的请求。

当前口径：

- UV：符合采集规则的请求按客户端 IP 写入当日 HyperLogLog
- DAU：符合采集规则且存在有效 JWT UUID subject 的请求，先映射到 analytics-only 整数 ordinal，再写入当日 bitmap
- 登录成功：`AuthService.login(...)` 成功后补记一次 DAU
- Redis 写入失败：只记录日志，不影响主业务响应

默认采集范围包括 `/api/posts/**`、`/api/search/**`、`/api/messages/**`、`/api/notices/**`、`/api/im-governance/**`。

`/api/analytics/**`、`/api/auth/**`、`/api/ops/**`、`/actuator/**`、`/internal/**`、`/files/**` 默认不由请求 filter 采集。
```

- [ ] **Step 3: Run config/docs checks**

Run:

```bash
git diff --check
rg -n "analytics\\.storage" backend/community-app/src/main backend/community-app/src/test/resources
rg -n "InMemoryAnalyticsRepository" backend/community-app/src/main
```

Expected:

- `git diff --check` exits 0
- both `rg` commands exit 1 with no output, which means production code/config did not reintroduce `analytics.storage` or `InMemoryAnalyticsRepository`

- [ ] **Step 4: Commit Task 6**

```bash
git add backend/community-app/src/main/resources/application.yml \
        backend/community-app/src/test/resources/application.yml \
        docs/business-logic/analytics-ingest-flow.md
git commit -m "docs: describe analytics ingest phase one"
```

---

### Task 7: Final Verification

**Files:**
- Verify all files changed in Tasks 1-6.

- [ ] **Step 1: Run targeted analytics and auth tests**

Run:

```bash
mvn -f backend/pom.xml -pl community-app -am -Dtest=AnalyticsRequestClassifierTest,AnalyticsPrincipalResolverTest,AnalyticsIngestServiceTest,AnalyticsRequestCaptureFilterTest,RedisAnalyticsUserOrdinalRepositoryTest,AnalyticsControllerUnitTest,AnalyticsRepositorySelectionTest,AnalyticsServiceTest,RedisAnalyticsRepositoryTest,AuthServiceLoginTest,RefreshTokenServiceTest test
```

Expected: all listed test classes pass.

- [ ] **Step 2: Run broader compile/test check for community-app**

Run:

```bash
mvn -f backend/pom.xml -pl community-app -am -DskipTests compile
```

Expected: reactor compile succeeds.

Do not use full `mvn -f backend/pom.xml -pl community-app -am test` as the completion gate unless the unrelated wallet and ArchUnit failures have been fixed. The current known unrelated failures are in:

- `AdminWalletOpsServiceTest`
- `InfraBoundaryArchTest`
- `DomainBoundaryArchTest`

- [ ] **Step 3: Verify worktree and residual references**

Run:

```bash
git status --short
rg -n "analytics\\.storage" backend/community-app/src/main backend/community-app/src/test/resources
rg -n "InMemoryAnalyticsRepository" backend/community-app/src/main
```

Expected:

- only intentional Phase 1 changes are present before final commit
- both `rg` commands exit 1 with no output, which means production code/config did not reintroduce the removed analytics storage switch or in-memory repository

- [ ] **Step 4: Final commit if previous task commits were not used**

If Tasks 1-6 were implemented without task-level commits, commit the whole Phase 1 change:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/analytics/ingest \
        backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/AnalyticsUserOrdinalRepository.java \
        backend/community-app/src/main/java/com/nowcoder/community/analytics/repo/RedisAnalyticsUserOrdinalRepository.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/service/AuthService.java \
        backend/community-app/src/main/resources/application.yml \
        backend/community-app/src/test/java/com/nowcoder/community/analytics/ingest \
        backend/community-app/src/test/java/com/nowcoder/community/analytics/repo/RedisAnalyticsUserOrdinalRepositoryTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/auth/service/AuthServiceLoginTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/auth/service/RefreshTokenServiceTest.java \
        backend/community-app/src/test/resources/application.yml \
        docs/business-logic/analytics-ingest-flow.md
git commit -m "feat: capture analytics uv dau traffic"
```

---

## Self-Review Notes

- Spec coverage: This plan covers Phase 1 only. Phase 2-5 remain future implementation plans.
- DAU UUID gap: The plan resolves the current UUID JWT subject vs. integer DAU bitmap mismatch through a Redis-backed analytics user ordinal map.
- Configuration coverage: `analytics.ingest.enabled`, `record-uv`, and `record-dau` are enforced in `AnalyticsIngestService`, so both request capture and login supplementation stay disabled until explicitly enabled.
- Trusted proxy scope: The plan reuses the existing `ClientIpResolver` and `gateway.trusted-proxy.*` safety model instead of adding a second analytics-specific proxy switch.
- Redis key scope: Phase 1 continues to use the existing `AnalyticsService` UV/DAU Redis keys; the versioned key migration from the spec remains a separate migration plan. The analytics user ordinal map and sequence keys use the shared `{analytics:user-ordinal}` hash tag so the Lua script remains Redis Cluster-safe.
- Placeholder scan: This plan contains concrete implementation and verification steps without unresolved markers.
- Type consistency: Request capture uses UUID user identity until the ingest service maps it to an integer ordinal; the existing `AnalyticsService.recordDau(LocalDate, int)` remains unchanged.
