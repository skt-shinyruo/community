# Login Rate Limit Redis Cluster Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce login rate-limit Redis round trips while preserving fail-closed behavior and Redis Cluster compatibility.

**Architecture:** Replace raw counter reads and writes with semantic single-key repository operations. Add a combined login precheck that returns captcha requirement and enforces block limits in one phase. Keep IP and username as independent Redis keys so Redis Cluster never needs cross-slot scripts.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring Data Redis `StringRedisTemplate`, Redis Lua scripts, JUnit 5, Mockito, AssertJ, Maven.

---

## File Structure

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/LoginRateLimitRepository.java`
  - Owns the auth-domain repository contract for login rate-limit counters.
  - Final shape exposes semantic `probe`, `incrementAndProbe`, and `delete` operations.

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisLoginRateLimitRepository.java`
  - Implements Redis Cluster-safe single-key Lua scripts.
  - Parses compact script results into repository result records.

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginRateLimitApplicationService.java`
  - Owns login rate-limit orchestration and fail-closed behavior.
  - Adds `precheck(...)` as the optimized hot-path API.

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginApplicationService.java`
  - Calls one combined rate-limit precheck before credential validation.
  - Stops calling `assertNotBlocked(...)` and `isCaptchaRequired(...)` sequentially.

- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/RedisLoginRateLimitRepositoryTest.java`
  - Verifies Redis scripts are single-key and parse script responses.

- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/LoginRateLimitApplicationServiceTest.java`
  - Verifies precheck behavior, fail-closed behavior, and semantic increment behavior.

- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/LoginApplicationServiceTest.java`
  - Verifies login uses one precheck call and no longer uses sequential rate-limit checks.

---

### Task 1: Add Semantic Redis Repository Operations

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/RedisLoginRateLimitRepositoryTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/LoginRateLimitRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisLoginRateLimitRepository.java`

- [ ] **Step 1: Write failing Redis repository tests**

Replace `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/RedisLoginRateLimitRepositoryTest.java` with:

```java
package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository.LoginRateLimitIncrement;
import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository.LoginRateLimitProbe;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisLoginRateLimitRepositoryTest {

    @Test
    void probeShouldUseSingleKeyScriptAndParseResult() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("auth:login:fail:ip:127.0.0.1")), eq("20"), eq("5")))
                .thenReturn(List.of(5L, 0L, 1L));
        RedisLoginRateLimitRepository repository = new RedisLoginRateLimitRepository(redisTemplate);

        LoginRateLimitProbe result = repository.probe("auth:login:fail:ip:127.0.0.1", 20, 5);

        assertThat(result.count()).isEqualTo(5);
        assertThat(result.blocked()).isFalse();
        assertThat(result.captchaRequired()).isTrue();
        ArgumentCaptor<RedisScript> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of("auth:login:fail:ip:127.0.0.1")), eq("20"), eq("5"));
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("redis.call('get'");
        assertThat(script.getScriptAsString()).contains("return {count, blocked, captchaRequired}");
    }

    @Test
    void incrementAndProbeShouldUseAtomicSingleKeyScriptAndParseResult() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("auth:login:fail:user:alice")), eq("60"), eq("5")))
                .thenReturn(List.of(5L, 1L));
        RedisLoginRateLimitRepository repository = new RedisLoginRateLimitRepository(redisTemplate);

        LoginRateLimitIncrement result = repository.incrementAndProbe("auth:login:fail:user:alice", 60, 5);

        assertThat(result.count()).isEqualTo(5);
        assertThat(result.blocked()).isTrue();
        ArgumentCaptor<RedisScript> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of("auth:login:fail:user:alice")), eq("60"), eq("5"));
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("redis.call('incr'");
        assertThat(script.getScriptAsString()).contains("redis.call('expire'");
        assertThat(script.getScriptAsString()).contains("return {count, blocked}");
    }

    @Test
    void probeShouldThrowWhenScriptReturnsMalformedResponse() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("auth:login:fail:ip:127.0.0.1")), eq("20"), eq("5")))
                .thenReturn(List.of(1L));
        RedisLoginRateLimitRepository repository = new RedisLoginRateLimitRepository(redisTemplate);

        assertThatThrownBy(() -> repository.probe("auth:login:fail:ip:127.0.0.1", 20, 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("redis login rate-limit script returned malformed result");
    }
}
```

- [ ] **Step 2: Run the Redis repository test and verify it fails**

Run from `backend`:

```bash
mvn test -pl :community-app -Dtest=RedisLoginRateLimitRepositoryTest
```

Expected: FAIL during compilation because `LoginRateLimitProbe`, `LoginRateLimitIncrement`, `probe`, and `incrementAndProbe` do not exist yet.

- [ ] **Step 3: Add transitional repository contract**

Replace `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/LoginRateLimitRepository.java` with this transitional version. It keeps raw methods temporarily so existing application code still compiles until Task 2 removes those calls.

```java
package com.nowcoder.community.auth.domain.repository;

public interface LoginRateLimitRepository {

    int count(String key);

    int increment(String key, int windowSeconds);

    LoginRateLimitProbe probe(String key, int blockLimit, int captchaThreshold);

    LoginRateLimitIncrement incrementAndProbe(String key, int windowSeconds, int blockLimit);

    void delete(String key);

    record LoginRateLimitProbe(int count, boolean blocked, boolean captchaRequired) {
    }

    record LoginRateLimitIncrement(int count, boolean blocked) {
    }
}
```

- [ ] **Step 4: Implement Redis semantic methods**

Replace `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisLoginRateLimitRepository.java` with:

```java
package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class RedisLoginRateLimitRepository implements LoginRateLimitRepository {

    private static final RedisScript<List> PROBE_SCRIPT = script(
            """
                    local raw = redis.call('get', KEYS[1])
                    local count = tonumber(raw)
                    if not count then
                        count = 0
                    end

                    local blockLimit = tonumber(ARGV[1])
                    if not blockLimit or blockLimit <= 0 then
                        blockLimit = 1
                    end

                    local captchaThreshold = tonumber(ARGV[2])
                    if not captchaThreshold then
                        captchaThreshold = 1
                    end

                    local blocked = 0
                    if count >= blockLimit then
                        blocked = 1
                    end

                    local captchaRequired = 0
                    if captchaThreshold <= 0 or count >= captchaThreshold then
                        captchaRequired = 1
                    end

                    return {count, blocked, captchaRequired}
                    """,
            List.class
    );

    private static final RedisScript<List> INCREMENT_WITH_TTL_SCRIPT = script(
            """
                    local count = redis.call('incr', KEYS[1])
                    if count == 1 then
                        redis.call('expire', KEYS[1], ARGV[1])
                    end

                    local blockLimit = tonumber(ARGV[2])
                    if not blockLimit or blockLimit <= 0 then
                        blockLimit = 1
                    end

                    local blocked = 0
                    if count >= blockLimit then
                        blocked = 1
                    end

                    return {count, blocked}
                    """,
            List.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisLoginRateLimitRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public int count(String key) {
        if (!StringUtils.hasText(key)) {
            return 0;
        }
        String value = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public int increment(String key, int windowSeconds) {
        return incrementAndProbe(key, windowSeconds, Integer.MAX_VALUE).count();
    }

    @Override
    public LoginRateLimitProbe probe(String key, int blockLimit, int captchaThreshold) {
        if (!StringUtils.hasText(key)) {
            return new LoginRateLimitProbe(0, false, false);
        }
        List<?> result = redisTemplate.execute(
                PROBE_SCRIPT,
                List.of(key),
                Integer.toString(Math.max(1, blockLimit)),
                Integer.toString(captchaThreshold)
        );
        return new LoginRateLimitProbe(
                intAt(result, 0),
                booleanAt(result, 1),
                booleanAt(result, 2)
        );
    }

    @Override
    public LoginRateLimitIncrement incrementAndProbe(String key, int windowSeconds, int blockLimit) {
        if (!StringUtils.hasText(key)) {
            return new LoginRateLimitIncrement(0, false);
        }
        List<?> result = redisTemplate.execute(
                INCREMENT_WITH_TTL_SCRIPT,
                List.of(key),
                Integer.toString(Math.max(1, windowSeconds)),
                Integer.toString(Math.max(1, blockLimit))
        );
        return new LoginRateLimitIncrement(
                intAt(result, 0),
                booleanAt(result, 1)
        );
    }

    @Override
    public void delete(String key) {
        if (StringUtils.hasText(key)) {
            redisTemplate.delete(key);
        }
    }

    private static int intAt(List<?> result, int index) {
        long value = longAt(result, index);
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private static boolean booleanAt(List<?> result, int index) {
        return longAt(result, index) != 0L;
    }

    private static long longAt(List<?> result, int index) {
        if (result == null || result.size() <= index) {
            throw new IllegalStateException("redis login rate-limit script returned malformed result");
        }
        Object value = result.get(index);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                throw new IllegalStateException("redis login rate-limit script returned malformed result");
            }
        }
        throw new IllegalStateException("redis login rate-limit script returned malformed result");
    }

    private static <T> RedisScript<T> script(String scriptText, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(resultType);
        return script;
    }
}
```

- [ ] **Step 5: Run Redis repository tests and verify they pass**

Run from `backend`:

```bash
mvn test -pl :community-app -Dtest=RedisLoginRateLimitRepositoryTest
```

Expected: PASS.

- [ ] **Step 6: Commit Task 1**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/LoginRateLimitRepository.java backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisLoginRateLimitRepository.java backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/RedisLoginRateLimitRepositoryTest.java
git commit -m "perf: add semantic login rate limit redis operations"
```

---

### Task 2: Add Combined Login Rate-Limit Precheck

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/LoginRateLimitApplicationServiceTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginRateLimitApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/LoginRateLimitRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisLoginRateLimitRepository.java`

- [ ] **Step 1: Write failing application-service tests**

Replace `backend/community-app/src/test/java/com/nowcoder/community/auth/application/LoginRateLimitApplicationServiceTest.java` with:

```java
package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.config.LoginRateLimitProperties;
import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository;
import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository.LoginRateLimitIncrement;
import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository.LoginRateLimitProbe;
import com.nowcoder.community.auth.domain.service.LoginRateLimitDomainService;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginRateLimitApplicationServiceTest {

    private final LoginRateLimitRepository loginRateLimitRepository = mock(LoginRateLimitRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<MeterRegistry> meterRegistryProvider = mock(ObjectProvider.class);

    private LoginRateLimitProperties properties;
    private LoginRateLimitApplicationService service;

    @BeforeEach
    void setUp() {
        properties = new LoginRateLimitProperties();
        service = new LoginRateLimitApplicationService(
                properties,
                loginRateLimitRepository,
                new LoginRateLimitDomainService(),
                meterRegistryProvider
        );
    }

    @Test
    void precheckShouldFailClosedWhenRepositoryProbeThrows() {
        when(loginRateLimitRepository.probe(anyString(), anyInt(), anyInt())).thenThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> service.precheck("alice", "127.0.0.1", "remote"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void isCaptchaRequiredShouldReturnTrueWhenRepositoryProbeThrows() {
        when(loginRateLimitRepository.probe(anyString(), anyInt(), anyInt())).thenThrow(new RuntimeException("redis down"));

        assertThat(service.isCaptchaRequired("alice", "127.0.0.1")).isTrue();
    }

    @Test
    void precheckShouldRequireCaptchaWhenIpProbeRequiresCaptcha() {
        when(loginRateLimitRepository.probe("auth:login:fail:ip:127.0.0.1", 20, 5))
                .thenReturn(new LoginRateLimitProbe(5, false, true));

        LoginRateLimitApplicationService.LoginRateLimitPrecheckResult result = service.precheck(null, "127.0.0.1", "remote");

        assertThat(result.captchaRequired()).isTrue();
        verify(loginRateLimitRepository).probe("auth:login:fail:ip:127.0.0.1", 20, 5);
        verify(loginRateLimitRepository, never()).probe("auth:login:fail:user:alice", 5, 2);
    }

    @Test
    void precheckShouldRequireCaptchaWhenUsernameProbeRequiresCaptcha() {
        when(loginRateLimitRepository.probe("auth:login:fail:user:alice", 5, 2))
                .thenReturn(new LoginRateLimitProbe(2, false, true));

        LoginRateLimitApplicationService.LoginRateLimitPrecheckResult result = service.precheck(" Alice ", null, "remote");

        assertThat(result.captchaRequired()).isTrue();
        verify(loginRateLimitRepository).probe("auth:login:fail:user:alice", 5, 2);
    }

    @Test
    void precheckShouldThrowIpTooManyRequestsWhenIpProbeIsBlocked() {
        when(loginRateLimitRepository.probe("auth:login:fail:ip:127.0.0.1", 20, 5))
                .thenReturn(new LoginRateLimitProbe(20, true, true));

        assertThatThrownBy(() -> service.precheck("alice", "127.0.0.1", "remote"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录尝试过于频繁")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    void precheckShouldThrowUsernameTooManyRequestsWhenUsernameProbeIsBlocked() {
        when(loginRateLimitRepository.probe("auth:login:fail:ip:127.0.0.1", 20, 5))
                .thenReturn(new LoginRateLimitProbe(0, false, false));
        when(loginRateLimitRepository.probe("auth:login:fail:user:alice", 5, 2))
                .thenReturn(new LoginRateLimitProbe(5, true, true));

        assertThatThrownBy(() -> service.precheck("alice", "127.0.0.1", "remote"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号登录尝试过于频繁")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    void precheckShouldTreatNonPositiveCaptchaThresholdAsRequiredAndStillProbeBlockLimit() {
        properties.setCaptchaRequiredFailuresPerIp(0);
        when(loginRateLimitRepository.probe("auth:login:fail:ip:127.0.0.1", 20, 0))
                .thenReturn(new LoginRateLimitProbe(0, false, true));

        LoginRateLimitApplicationService.LoginRateLimitPrecheckResult result = service.precheck(null, "127.0.0.1", "remote");

        assertThat(result.captchaRequired()).isTrue();
        verify(loginRateLimitRepository).probe("auth:login:fail:ip:127.0.0.1", 20, 0);
    }

    @Test
    void recordFailureShouldDelegateIncrementAndProbeWithNormalizedKeyAndWindow() {
        when(loginRateLimitRepository.incrementAndProbe("auth:login:fail:ip:127.0.0.1", 60, 20))
                .thenReturn(new LoginRateLimitIncrement(1, false));

        service.recordFailure(null, "127.0.0.1", "remote");

        verify(loginRateLimitRepository).incrementAndProbe("auth:login:fail:ip:127.0.0.1", 60, 20);
    }

    @Test
    void recordFailureShouldThrowWhenIpIncrementResultIsBlocked() {
        when(loginRateLimitRepository.incrementAndProbe("auth:login:fail:ip:127.0.0.1", 60, 20))
                .thenReturn(new LoginRateLimitIncrement(20, true));

        assertThatThrownBy(() -> service.recordFailure(null, "127.0.0.1", "remote"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    void recordFailureShouldFailClosedWhenRepositoryIncrementThrows() {
        when(loginRateLimitRepository.incrementAndProbe("auth:login:fail:ip:127.0.0.1", 60, 20))
                .thenThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> service.recordFailure(null, "127.0.0.1", "remote"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }
}
```

- [ ] **Step 2: Run the application-service test and verify it fails**

Run from `backend`:

```bash
mvn test -pl :community-app -Dtest=LoginRateLimitApplicationServiceTest
```

Expected: FAIL because `precheck(...)` does not exist and `recordFailure(...)` still calls the raw `increment(...)` repository method.

- [ ] **Step 3: Implement combined precheck and semantic increment use**

Replace `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginRateLimitApplicationService.java` with:

```java
package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.config.LoginRateLimitProperties;
import com.nowcoder.community.auth.domain.model.LoginRateLimitKey;
import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository;
import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository.LoginRateLimitIncrement;
import com.nowcoder.community.auth.domain.repository.LoginRateLimitRepository.LoginRateLimitProbe;
import com.nowcoder.community.auth.domain.service.LoginRateLimitDomainService;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

@Service
public class LoginRateLimitApplicationService {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitApplicationService.class);
    private static final String KEY_PREFIX = "auth:login:fail:";
    private static final String KEY_PREFIX_IP = KEY_PREFIX + "ip:";
    private static final String KEY_PREFIX_USER = KEY_PREFIX + "user:";
    private static final String METRIC = "auth_login_rate_limit_total";

    private final LoginRateLimitProperties properties;
    private final LoginRateLimitRepository loginRateLimitRepository;
    private final LoginRateLimitDomainService loginRateLimitDomainService;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public LoginRateLimitApplicationService(
            LoginRateLimitProperties properties,
            LoginRateLimitRepository loginRateLimitRepository,
            LoginRateLimitDomainService loginRateLimitDomainService,
            ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        this.properties = properties;
        this.loginRateLimitRepository = loginRateLimitRepository;
        this.loginRateLimitDomainService = loginRateLimitDomainService;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    public LoginRateLimitPrecheckResult precheck(String username, String ip, String ipSource) {
        if (!properties.isEnabled()) {
            return new LoginRateLimitPrecheckResult(false);
        }

        try {
            int ipLimit = Math.max(1, properties.getMaxFailuresPerIp());
            int userLimit = Math.max(1, properties.getMaxFailuresPerUser());
            int ipCaptchaThreshold = properties.getCaptchaRequiredFailuresPerIp();
            int userCaptchaThreshold = properties.getCaptchaRequiredFailuresPerUser();
            LoginRateLimitKey key = loginRateLimitDomainService.keyOf(username, ip);

            boolean captchaRequired = false;
            if (StringUtils.hasText(key.ip())) {
                LoginRateLimitProbe probe = loginRateLimitRepository.probe(ipKey(key), ipLimit, ipCaptchaThreshold);
                if (probe.blocked()) {
                    record("blocked", ipSource);
                    throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试");
                }
                captchaRequired = captchaRequired || probe.captchaRequired();
            }
            if (StringUtils.hasText(key.username())) {
                LoginRateLimitProbe probe = loginRateLimitRepository.probe(userKey(key), userLimit, userCaptchaThreshold);
                if (probe.blocked()) {
                    record("blocked", ipSource);
                    throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "账号登录尝试过于频繁，请稍后再试");
                }
                captchaRequired = captchaRequired || probe.captchaRequired();
            }
            record("allowed", ipSource);
            return new LoginRateLimitPrecheckResult(captchaRequired);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            record("dependency_error", ipSource);
            log.warn("[auth][login-rate-limit] precheck failed: {}", e.toString());
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "登录风控暂时不可用，请稍后重试");
        }
    }

    public void assertNotBlocked(String username, String ip, String ipSource) {
        precheck(username, ip, ipSource);
    }

    public void recordFailure(String username, String ip, String ipSource) {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            int ipLimit = Math.max(1, properties.getMaxFailuresPerIp());
            int userLimit = Math.max(1, properties.getMaxFailuresPerUser());
            int windowSeconds = Math.max(1, properties.getWindowSeconds());
            LoginRateLimitKey key = loginRateLimitDomainService.keyOf(username, ip);

            if (StringUtils.hasText(key.ip())) {
                LoginRateLimitIncrement ipResult = loginRateLimitRepository.incrementAndProbe(ipKey(key), windowSeconds, ipLimit);
                if (ipResult.blocked()) {
                    record("blocked", ipSource);
                    throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试");
                }
            }
            if (StringUtils.hasText(key.username())) {
                LoginRateLimitIncrement userResult = loginRateLimitRepository.incrementAndProbe(userKey(key), windowSeconds, userLimit);
                if (userResult.blocked()) {
                    record("blocked", ipSource);
                    throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "账号登录尝试过于频繁，请稍后再试");
                }
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

    public void reset(String username, String ip) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            LoginRateLimitKey key = loginRateLimitDomainService.keyOf(username, ip);
            if (StringUtils.hasText(key.ip())) {
                loginRateLimitRepository.delete(ipKey(key));
            }
            if (StringUtils.hasText(key.username())) {
                loginRateLimitRepository.delete(userKey(key));
            }
        } catch (RuntimeException e) {
            record("dependency_error", null);
            log.warn("[auth][login-rate-limit] reset failed: {}", e.toString());
        }
    }

    public boolean isCaptchaRequired(String username, String ip) {
        if (!properties.isEnabled()) {
            return false;
        }

        try {
            int ipLimit = Math.max(1, properties.getMaxFailuresPerIp());
            int userLimit = Math.max(1, properties.getMaxFailuresPerUser());
            int ipThreshold = properties.getCaptchaRequiredFailuresPerIp();
            int userThreshold = properties.getCaptchaRequiredFailuresPerUser();
            LoginRateLimitKey key = loginRateLimitDomainService.keyOf(username, ip);

            if (StringUtils.hasText(key.ip())) {
                LoginRateLimitProbe probe = loginRateLimitRepository.probe(ipKey(key), ipLimit, ipThreshold);
                if (probe.captchaRequired()) {
                    return true;
                }
            }
            if (StringUtils.hasText(key.username())) {
                LoginRateLimitProbe probe = loginRateLimitRepository.probe(userKey(key), userLimit, userThreshold);
                if (probe.captchaRequired()) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            record("dependency_error", null);
            log.warn("[auth][login-rate-limit] isCaptchaRequired failed: {}", e.toString());
            return true;
        }
    }

    private void record(String outcome, String ipSource) {
        MeterRegistry meterRegistry = meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            return;
        }
        String source = StringUtils.hasText(ipSource) ? ipSource.trim().toLowerCase(Locale.ROOT) : "unknown";
        if (!"remote".equals(source) && !"xff".equals(source)) {
            source = "unknown";
        }
        String o = StringUtils.hasText(outcome) ? outcome.trim().toLowerCase(Locale.ROOT) : "unknown";
        meterRegistry.counter(METRIC, Tags.of("outcome", o, "ip_source", source)).increment();
    }

    private String ipKey(LoginRateLimitKey key) {
        return KEY_PREFIX_IP + key.ip();
    }

    private String userKey(LoginRateLimitKey key) {
        return KEY_PREFIX_USER + key.username();
    }

    public record LoginRateLimitPrecheckResult(boolean captchaRequired) {
    }
}
```

- [ ] **Step 4: Run the application-service test and verify it passes**

Run from `backend`:

```bash
mvn test -pl :community-app -Dtest=LoginRateLimitApplicationServiceTest
```

Expected: PASS.

- [ ] **Step 5: Remove raw counter methods from repository contract**

Replace `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/LoginRateLimitRepository.java` with the final semantic-only contract:

```java
package com.nowcoder.community.auth.domain.repository;

public interface LoginRateLimitRepository {

    LoginRateLimitProbe probe(String key, int blockLimit, int captchaThreshold);

    LoginRateLimitIncrement incrementAndProbe(String key, int windowSeconds, int blockLimit);

    void delete(String key);

    record LoginRateLimitProbe(int count, boolean blocked, boolean captchaRequired) {
    }

    record LoginRateLimitIncrement(int count, boolean blocked) {
    }
}
```

Remove these two methods from `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisLoginRateLimitRepository.java`:

```java
@Override
public int count(String key) {
    if (!StringUtils.hasText(key)) {
        return 0;
    }
    String value = redisTemplate.opsForValue().get(key);
    if (!StringUtils.hasText(value)) {
        return 0;
    }
    try {
        return Integer.parseInt(value);
    } catch (NumberFormatException e) {
        return 0;
    }
}

@Override
public int increment(String key, int windowSeconds) {
    return incrementAndProbe(key, windowSeconds, Integer.MAX_VALUE).count();
}
```

- [ ] **Step 6: Run repository and service tests after cleanup**

Run from `backend`:

```bash
mvn test -pl :community-app -Dtest=LoginRateLimitApplicationServiceTest,RedisLoginRateLimitRepositoryTest
```

Expected: PASS.

- [ ] **Step 7: Commit Task 2**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginRateLimitApplicationService.java backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/LoginRateLimitRepository.java backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisLoginRateLimitRepository.java backend/community-app/src/test/java/com/nowcoder/community/auth/application/LoginRateLimitApplicationServiceTest.java
git commit -m "perf: combine login rate limit precheck"
```

---

### Task 3: Use Combined Precheck In Login Flow

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/LoginApplicationServiceTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginApplicationService.java`

- [ ] **Step 1: Add failing login-flow tests**

In `backend/community-app/src/test/java/com/nowcoder/community/auth/application/LoginApplicationServiceTest.java`, add these imports if they are not present:

```java
import com.nowcoder.community.auth.application.LoginRateLimitApplicationService.LoginRateLimitPrecheckResult;
```

In the existing `setUp()` method, add the default precheck stub after `authService` is constructed:

```java
when(loginRateLimitService.precheck(any(), any(), any()))
        .thenReturn(new LoginRateLimitPrecheckResult(false));
```

Add this test after `loginShouldRecordDauSupplementAfterSuccessfulAuthentication`:

```java
@Test
void loginShouldUseSingleRateLimitPrecheckWhenCaptchaIsNotRequired() {
    UUID userId = uuid(7);
    UserCredentialView user = new UserCredentialView(userId, "alice", 1, 0, "h1");
    when(loginRateLimitService.precheck("alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE))
            .thenReturn(new LoginRateLimitPrecheckResult(false));
    when(userCredentialQueryApi.authenticate("alice", "secret")).thenReturn(UserAuthenticationResultView.authenticated(user));
    when(userCredentialQueryApi.authoritiesOf(user)).thenReturn(List.of("ROLE_USER"));
    when(authTokenPort.createAccessToken(eq(userId), eq("alice"), eq(List.of("ROLE_USER")))).thenReturn("access-token");
    when(refreshTokenService.issue(userId)).thenReturn(new RefreshTokenApplicationService.IssuedRefreshToken("rt", issuedCookie("rt")));

    LoginResult result = authService.login(loginCommand("alice", "secret", null, null));

    assertThat(result.accessToken()).isEqualTo("access-token");
    verify(loginRateLimitService).precheck("alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
    verify(loginRateLimitService, never()).assertNotBlocked(any(), any(), any());
    verify(loginRateLimitService, never()).isCaptchaRequired(any(), any());
}
```

Update the existing captcha tests in the same file:

```java
@Test
void loginShouldLogDeniedWhenCaptchaIsRequiredButMissing(CapturedOutput output) {
    when(loginRateLimitService.precheck("alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE))
            .thenReturn(new LoginRateLimitPrecheckResult(true));

    Throwable thrown = catchThrowable(() -> authService.login(loginCommand("alice", "secret", "cid", "")));

    assertThat(thrown).isInstanceOf(BusinessException.class);
    BusinessException error = (BusinessException) thrown;
    assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.CAPTCHA_REQUIRED);
    verify(loginRateLimitService).recordFailure("alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
    assertThat(output.getAll())
            .contains("community.category=security")
            .contains("community.action=login")
            .contains("community.outcome=denied")
            .contains("community.reason_code=captcha_required")
            .contains("username=alice")
            .contains("source.ip=127.0.0.1")
            .doesNotContain("secret")
            .doesNotContain("cid");
}

@Test
void loginShouldLogDeniedWhenCaptchaIsInvalid(CapturedOutput output) {
    when(loginRateLimitService.precheck("alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE))
            .thenReturn(new LoginRateLimitPrecheckResult(true));
    when(captchaService.verify("cid", "bad-code")).thenReturn(false);

    Throwable thrown = catchThrowable(() -> authService.login(loginCommand("alice", "secret", "cid", "bad-code")));

    assertThat(thrown).isInstanceOf(BusinessException.class);
    BusinessException error = (BusinessException) thrown;
    assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.CAPTCHA_INVALID);
    verify(loginRateLimitService).recordFailure("alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
    assertThat(output.getAll())
            .contains("community.category=security")
            .contains("community.action=login")
            .contains("community.outcome=denied")
            .contains("community.reason_code=captcha_invalid")
            .contains("username=alice")
            .contains("source.ip=127.0.0.1")
            .doesNotContain("secret")
            .doesNotContain("bad-code")
            .doesNotContain("cid");
}
```

- [ ] **Step 2: Run the login application test and verify it fails**

Run from `backend`:

```bash
mvn test -pl :community-app -Dtest=LoginApplicationServiceTest
```

Expected: FAIL because `LoginApplicationService` still calls `assertNotBlocked(...)` and `isCaptchaRequired(...)` instead of `precheck(...)`.

- [ ] **Step 3: Update login flow to call one precheck**

In `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginApplicationService.java`, replace this block:

```java
loginRateLimitService.assertNotBlocked(username, ip, ipSource);

if (loginRateLimitService.isCaptchaRequired(username, ip)) {
```

with:

```java
LoginRateLimitApplicationService.LoginRateLimitPrecheckResult rateLimit = loginRateLimitService.precheck(username, ip, ipSource);

if (rateLimit.captchaRequired()) {
```

Do not change the existing captcha-required and captcha-invalid handling inside that `if` block. It must continue to call `recordFailure(...)`, log the same security event, and throw the same `AuthErrorCode` values.

- [ ] **Step 4: Run the login application test and verify it passes**

Run from `backend`:

```bash
mvn test -pl :community-app -Dtest=LoginApplicationServiceTest
```

Expected: PASS.

- [ ] **Step 5: Commit Task 3**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginApplicationService.java backend/community-app/src/test/java/com/nowcoder/community/auth/application/LoginApplicationServiceTest.java
git commit -m "perf: use combined login rate limit precheck"
```

---

### Task 4: Verify The Hot Path Optimization

**Files:**
- Verify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginApplicationService.java`
- Verify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginRateLimitApplicationService.java`
- Verify: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisLoginRateLimitRepository.java`

- [ ] **Step 1: Run focused tests**

Run from `backend`:

```bash
mvn test -pl :community-app -Dtest=LoginRateLimitApplicationServiceTest,RedisLoginRateLimitRepositoryTest,LoginApplicationServiceTest
```

Expected: PASS.

- [ ] **Step 2: Run auth-related tests**

Run from `backend`:

```bash
mvn test -pl :community-app -Dtest='*Auth*Test,*Login*Test,*Registration*Test,*PasswordReset*Test'
```

Expected: PASS.

- [ ] **Step 3: Verify no raw counter API remains**

Use the code search tool or run from the repository root:

```bash
rg "count\(|increment\(" backend/community-app/src/main/java/com/nowcoder/community/auth
```

Expected: no `LoginRateLimitRepository.count(...)` or `LoginRateLimitRepository.increment(...)` usages remain. Matches for Redis Lua text such as `redis.call('incr'` are acceptable.

- [ ] **Step 4: Verify login no longer does sequential precheck calls**

Use the code search tool or run from the repository root:

```bash
rg "assertNotBlocked\(|isCaptchaRequired\(" backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginApplicationService.java
```

Expected: no matches.

- [ ] **Step 5: Commit verification-only fixes if any were needed**

If Step 1 through Step 4 required any code changes, commit them:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth backend/community-app/src/test/java/com/nowcoder/community/auth
git commit -m "test: verify login rate limit redis optimization"
```

If no files changed during Task 4, do not create an empty commit.
