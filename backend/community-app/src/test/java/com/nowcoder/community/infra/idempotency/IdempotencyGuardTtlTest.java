package com.nowcoder.community.common.idempotency;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyGuardTtlTest {

    private static JsonCodec jsonCodec() {
        return new JacksonJsonCodec(JsonMappers.standard());
    }

    @Test
    void executeRequiredShouldUseConfiguredTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = (ValueOperations<String, String>) mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("P\nhash-1"), eq(Duration.ofSeconds(45)))).thenReturn(Boolean.TRUE);

        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> meterRegistryProvider = (ObjectProvider<MeterRegistry>) mock(ObjectProvider.class);
        when(meterRegistryProvider.getIfAvailable()).thenReturn(null);

        IdempotencyProperties properties = new IdempotencyProperties();
        properties.setProcessingTtl(Duration.ofSeconds(45));
        properties.setSuccessTtl(Duration.ofMinutes(10));

        IdempotencyGuard guard = new IdempotencyGuard(
                jsonCodec(),
                new RedisIdempotencyStore(redisTemplate),
                meterRegistryProvider,
                properties
        );
        UUID userId = uuid(1);

        guard.executeRequired("op", userId, "k1", "hash-1", null, String.class, () -> "OK");

        verify(valueOps).setIfAbsent(anyString(), eq("P\nhash-1"), eq(Duration.ofSeconds(45)));
        verify(valueOps).set(anyString(), anyString(), eq(Duration.ofMinutes(10)));
    }
}
