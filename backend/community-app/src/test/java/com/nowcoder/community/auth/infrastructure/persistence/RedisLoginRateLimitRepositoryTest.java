package com.nowcoder.community.auth.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisLoginRateLimitRepositoryTest {

    @Test
    void incrementShouldUseAtomicIncrementScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("auth:login:fail:ip:127.0.0.1")), eq("60")))
                .thenReturn(1L);
        RedisLoginRateLimitRepository repository = new RedisLoginRateLimitRepository(redisTemplate);

        int count = repository.increment("auth:login:fail:ip:127.0.0.1", 60);

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of("auth:login:fail:ip:127.0.0.1")), eq("60"));
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("redis.call('incr'");
        assertThat(script.getScriptAsString()).contains("redis.call('expire'");
    }

    @Test
    void tryAcquireShouldAtomicallyEnforceTheInFlightLimitAndLease() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID token = UUID.fromString("00000000-0000-7000-8000-000000000001");
        String failureKey = "auth:login:fail:user:alice";
        String leaseKey = "auth:login:inflight:{auth:login:fail:user:alice}:auth:login:fail:user:alice";
        when(redisTemplate.execute(any(RedisScript.class),
                eq(List.of(failureKey, leaseKey)),
                eq("5"), eq(token.toString()), eq("30000")))
                .thenReturn(1L);
        RedisLoginRateLimitRepository repository = new RedisLoginRateLimitRepository(redisTemplate);

        assertThat(repository.tryAcquire(failureKey, leaseKey, token, 5, 30_000)).isTrue();

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(),
                eq(List.of(failureKey, leaseKey)),
                eq("5"), eq(token.toString()), eq("30000"));
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("redis.call('time')");
        assertThat(script.getScriptAsString()).contains("local failures = committedFailures(KEYS[1])");
        assertThat(script.getScriptAsString()).contains("string.match(raw_count, '^[1-9][0-9]*$')");
        assertThat(script.getScriptAsString()).contains("redis.call('pttl', key) <= 0");
        assertThat(script.getScriptAsString()).contains("redis.call('zremrangebyscore', KEYS[2]");
        assertThat(script.getScriptAsString()).contains("failures + in_flight >= tonumber(ARGV[1])");
        assertThat(script.getScriptAsString()).contains("redis.call('zadd', KEYS[2]");
        assertThat(script.getScriptAsString()).contains("redis.call('zrevrange', KEYS[2]");
        assertThat(script.getScriptAsString()).contains("redis.call('pexpireat', KEYS[2]");
    }

    @Test
    void tryAcquireShouldFailClosedForMalformedCommittedFailureBudget() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID token = UUID.fromString("00000000-0000-7000-8000-000000000001");
        String failureKey = "auth:login:fail:user:alice";
        String leaseKey = "auth:login:inflight:{auth:login:fail:user:alice}:auth:login:fail:user:alice";
        when(redisTemplate.execute(any(RedisScript.class),
                eq(List.of(failureKey, leaseKey)),
                eq("5"), eq(token.toString()), eq("30000")))
                .thenReturn(-1L);
        RedisLoginRateLimitRepository repository = new RedisLoginRateLimitRepository(redisTemplate);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> repository.tryAcquire(failureKey, leaseKey, token, 5, 30_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failure budget");
    }

    @Test
    void countBudgetShouldAtomicallyIncludeCommittedAndLiveReservations() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        String failureKey = "auth:login:fail:user:alice";
        String leaseKey = "auth:login:inflight:{auth:login:fail:user:alice}:auth:login:fail:user:alice";
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(failureKey, leaseKey))))
                .thenReturn(3L);
        RedisLoginRateLimitRepository repository = new RedisLoginRateLimitRepository(redisTemplate);

        assertThat(repository.countBudget(failureKey, leaseKey)).isEqualTo(3);

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of(failureKey, leaseKey)));
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("redis.call('time')");
        assertThat(script.getScriptAsString()).contains("local failures = committedFailures(KEYS[1])");
        assertThat(script.getScriptAsString()).contains("string.match(raw_count, '^[1-9][0-9]*$')");
        assertThat(script.getScriptAsString()).contains("redis.call('pttl', key) <= 0");
        assertThat(script.getScriptAsString()).contains("redis.call('zremrangebyscore', KEYS[2]");
        assertThat(script.getScriptAsString()).contains("failures + redis.call('zcard', KEYS[2])");
    }

    @Test
    void releaseShouldAtomicallyDecrementOrDeleteTheInFlightCounter() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID token = UUID.fromString("00000000-0000-7000-8000-000000000001");
        when(redisTemplate.execute(any(RedisScript.class),
                eq(List.of("auth:login:inflight:user:alice")), eq(token.toString())))
                .thenReturn(0L);
        RedisLoginRateLimitRepository repository = new RedisLoginRateLimitRepository(redisTemplate);

        repository.release("auth:login:inflight:user:alice", token);

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(),
                eq(List.of("auth:login:inflight:user:alice")), eq(token.toString()));
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("redis.call('zrem', KEYS[1], ARGV[1])");
        assertThat(script.getScriptAsString()).contains("redis.call('del', KEYS[1])");
        assertThat(script.getScriptAsString()).contains("redis.call('zcard', KEYS[1])");
    }

    @Test
    void renewShouldExtendOnlyAnUnexpiredOwnerTokenUsingRedisTime() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID token = UUID.fromString("00000000-0000-7000-8000-000000000001");
        String leaseKey = "auth:login:inflight:{auth:login:fail:user:alice}:auth:login:fail:user:alice";
        when(redisTemplate.execute(any(RedisScript.class),
                eq(List.of(leaseKey)), eq(token.toString()), eq("120000")))
                .thenReturn(1L);
        RedisLoginRateLimitRepository repository = new RedisLoginRateLimitRepository(redisTemplate);

        assertThat(repository.renew(leaseKey, token, 120_000)).isTrue();

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(),
                eq(List.of(leaseKey)), eq(token.toString()), eq("120000"));
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("redis.call('time')");
        assertThat(script.getScriptAsString()).contains("redis.call('zscore', KEYS[1], ARGV[1])");
        assertThat(script.getScriptAsString()).contains("tonumber(expires_at) <= now_ms");
        assertThat(script.getScriptAsString()).contains("redis.call('zadd', KEYS[1], 'XX'");
        assertThat(script.getScriptAsString()).contains("redis.call('zrevrange', KEYS[1]");
        assertThat(script.getScriptAsString()).contains("redis.call('pexpireat', KEYS[1]");
    }
}
