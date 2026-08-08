package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.application.port.RegistrationRateLimitPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.ClusterSlotHashUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisRegistrationRateLimitAdapterTest {

    @Test
    void tryConsumeShouldRunOneAllOrNoneScriptWithEveryBucketInOneClusterSlot() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        List<RegistrationRateLimitPort.Quota> quotas = quotas();
        List<String> keys = quotas.stream()
                .map(quota -> RedisRegistrationRateLimitAdapter.quotaKey(
                        RegistrationRateLimitPort.Flow.RESEND, quota))
                .toList();
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys),
                eq("3600000"), eq("20"), eq("5"), eq("5")
        )).thenReturn(1L);
        RedisRegistrationRateLimitAdapter adapter = new RedisRegistrationRateLimitAdapter(redisTemplate);

        assertThat(adapter.tryConsume(
                RegistrationRateLimitPort.Flow.RESEND,
                Duration.ofHours(1),
                quotas
        )).isTrue();

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                eq(keys),
                eq("3600000"), eq("20"), eq("5"), eq("5")
        );
        assertThat(keys).allSatisfy(key -> assertThat(key)
                .startsWith("auth:registration:quota:{registration-quota}:resend:")
                .doesNotContain("alice@example.com", "127.0.0.1"));
        int slot = ClusterSlotHashUtil.calculateSlot(keys.get(0));
        assertThat(keys).allSatisfy(key ->
                assertThat(ClusterSlotHashUtil.calculateSlot(key)).isEqualTo(slot));

        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        String lua = script.getScriptAsString();
        assertThat(lua).contains(
                "redis.call('GET', KEYS[i])",
                "redis.call('PTTL', KEYS[i])",
                "string.match(raw_count, '^[1-9][0-9]*$')",
                "if count >= maximum then",
                "redis.call('INCR', KEYS[i])",
                "redis.call('PEXPIRE', KEYS[i], window_ms)"
        );
        assertThat(lua.indexOf("if count >= maximum then"))
                .isLessThan(lua.indexOf("redis.call('INCR', KEYS[i])"));
    }

    @Test
    void tryConsumeShouldReturnFalseForAnExhaustedBucket() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        List<RegistrationRateLimitPort.Quota> quotas = quotas();
        List<String> keys = quotaKeys(quotas);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys),
                eq("3600000"), eq("20"), eq("5"), eq("5")
        )).thenReturn(0L);
        RedisRegistrationRateLimitAdapter adapter = new RedisRegistrationRateLimitAdapter(redisTemplate);

        assertThat(adapter.tryConsume(
                RegistrationRateLimitPort.Flow.RESEND,
                Duration.ofHours(1),
                quotas
        )).isFalse();
    }

    @Test
    void tryConsumeShouldFailClosedForMalformedRedisState() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        List<RegistrationRateLimitPort.Quota> quotas = quotas();
        List<String> keys = quotaKeys(quotas);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys),
                eq("3600000"), eq("20"), eq("5"), eq("5")
        )).thenReturn(-1L);
        RedisRegistrationRateLimitAdapter adapter = new RedisRegistrationRateLimitAdapter(redisTemplate);

        assertThatThrownBy(() -> adapter.tryConsume(
                RegistrationRateLimitPort.Flow.RESEND,
                Duration.ofHours(1),
                quotas
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("malformed");
    }

    @Test
    void tryConsumeShouldRejectNonOpaqueIdentifiersBeforeCallingRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisRegistrationRateLimitAdapter adapter = new RedisRegistrationRateLimitAdapter(redisTemplate);
        RegistrationRateLimitPort.Quota quota = new RegistrationRateLimitPort.Quota(
                RegistrationRateLimitPort.Dimension.EMAIL,
                "alice@example.com",
                5
        );

        assertThatThrownBy(() -> adapter.tryConsume(
                RegistrationRateLimitPort.Flow.RESEND,
                Duration.ofHours(1),
                List.of(quota)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("opaque");
        verifyNoInteractions(redisTemplate);
    }

    private static List<String> quotaKeys(List<RegistrationRateLimitPort.Quota> quotas) {
        return quotas.stream()
                .map(quota -> RedisRegistrationRateLimitAdapter.quotaKey(
                        RegistrationRateLimitPort.Flow.RESEND, quota))
                .toList();
    }

    private static List<RegistrationRateLimitPort.Quota> quotas() {
        return List.of(
                new RegistrationRateLimitPort.Quota(
                        RegistrationRateLimitPort.Dimension.IP, "opaque-ip", 20),
                new RegistrationRateLimitPort.Quota(
                        RegistrationRateLimitPort.Dimension.EMAIL, "opaque-email", 5),
                new RegistrationRateLimitPort.Quota(
                        RegistrationRateLimitPort.Dimension.REGISTRATION, "opaque-registration", 5)
        );
    }
}
