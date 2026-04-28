package com.nowcoder.community.analytics.infrastructure.persistence;

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

        int ordinal = repository.ordinalOf(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        assertThat(ordinal).isEqualTo(7);
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), eq("11111111-1111-1111-1111-111111111111"));
        assertThat(keys.getValue()).containsExactly("{analytics:user-ordinal}:map", "{analytics:user-ordinal}:seq");
    }
}
