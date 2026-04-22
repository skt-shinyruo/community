package com.nowcoder.community.content.score;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisPostScoreQueueTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @SuppressWarnings("unchecked")
    private final ZSetOperations<String, String> zsetOps = mock(ZSetOperations.class);

    @Test
    void reenqueueShouldUseAtomicRetryHashIncrementAndExpireScript() {
        when(redisTemplate.opsForZSet()).thenReturn(zsetOps);
        when(redisTemplate.execute(any(RedisScript.class), any(), anyString(), anyString())).thenReturn(1L);
        UUID postId = uuid(123);

        RedisPostScoreQueue queue = new RedisPostScoreQueue(redisTemplate);
        queue.reenqueue(postId);

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                eq(List.of("post:score:retry")),
                eq(postId.toString()),
                eq(Long.toString(Duration.ofDays(3).toMillis()))
        );
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("hincrby");
        assertThat(script.getScriptAsString()).contains("pexpire");
        verify(redisTemplate, never()).expire(eq("post:score:retry"), any(Duration.class));
    }
}
