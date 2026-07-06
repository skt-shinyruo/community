package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.HotFeedProjectionGuard;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisHotFeedProjectionGuardTest {

    @Test
    void tryBeginShouldAcquirePerPostLockWithAtomicScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID postId = uuid(99);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(
                        "post:feed:hot:projection:lock:" + postId,
                        "post:feed:hot:projection:event:evt-1",
                        "post:feed:hot:projection:version:" + postId
                )),
                eq("42"),
                anyString(),
                eq("30000")
        )).thenReturn(1L);
        RedisHotFeedProjectionGuard guard = new RedisHotFeedProjectionGuard(redisTemplate);

        HotFeedProjectionGuard.ProjectionAttempt attempt = guard.tryBegin(postId, "evt-1", 42L);

        assertThat(attempt.accepted()).isTrue();
        assertThat(attempt.token()).isNotBlank();
    }

    @Test
    void tryBeginShouldRejectBlankEventIdWithoutRedisCall() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisHotFeedProjectionGuard guard = new RedisHotFeedProjectionGuard(redisTemplate);

        assertThat(guard.tryBegin(uuid(99), " ", 42L).accepted()).isFalse();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void tryBeginShouldRejectDuplicateOrStaleEventsFromScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID postId = uuid(99);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(
                        "post:feed:hot:projection:lock:" + postId,
                        "post:feed:hot:projection:event:evt-1",
                        "post:feed:hot:projection:version:" + postId
                )),
                eq("42"),
                anyString(),
                eq("30000")
        )).thenReturn(-1L);
        RedisHotFeedProjectionGuard guard = new RedisHotFeedProjectionGuard(redisTemplate);

        assertThat(guard.tryBegin(postId, "evt-1", 42L).accepted()).isFalse();
    }

    @Test
    void sameTimestampDistinctEventsShouldNotBeRejectedAsStaleByScripts() throws Exception {
        assertThat(script("BEGIN_SCRIPT").getScriptAsString())
                .contains("currentNumber > next")
                .doesNotContain("currentNumber >= next");
        assertThat(script("CURRENT_SCRIPT").getScriptAsString())
                .contains("currentNumber > next")
                .doesNotContain("currentNumber >= next");
    }

    @Test
    void isCurrentShouldCheckLeaseTokenAndCommittedVersion() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID postId = uuid(99);
        HotFeedProjectionGuard.ProjectionAttempt attempt = HotFeedProjectionGuard.ProjectionAttempt.accepted(
                postId,
                "evt-1",
                42L,
                "token-1"
        );
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(
                        "post:feed:hot:projection:lock:" + postId,
                        "post:feed:hot:projection:version:" + postId
                )),
                eq("token-1"),
                eq("42")
        )).thenReturn(1L);
        RedisHotFeedProjectionGuard guard = new RedisHotFeedProjectionGuard(redisTemplate);

        assertThat(guard.isCurrent(attempt)).isTrue();

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(
                        "post:feed:hot:projection:lock:" + postId,
                        "post:feed:hot:projection:version:" + postId
                )),
                eq("token-1"),
                eq("42")
        );
    }

    @Test
    void commitShouldMarkEventAndVersionThenReleaseLock() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID postId = uuid(99);
        HotFeedProjectionGuard.ProjectionAttempt attempt = HotFeedProjectionGuard.ProjectionAttempt.accepted(
                postId,
                "evt-1",
                42L,
                "token-1"
        );
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(
                        "post:feed:hot:projection:lock:" + postId,
                        "post:feed:hot:projection:event:evt-1",
                        "post:feed:hot:projection:version:" + postId
                )),
                eq("token-1"),
                eq("604800"),
                eq("42")
        )).thenReturn(1L);
        RedisHotFeedProjectionGuard guard = new RedisHotFeedProjectionGuard(redisTemplate);

        guard.commit(attempt);

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(
                        "post:feed:hot:projection:lock:" + postId,
                        "post:feed:hot:projection:event:evt-1",
                        "post:feed:hot:projection:version:" + postId
                )),
                eq("token-1"),
                eq("604800"),
                eq("42")
        );
    }

    @Test
    void abortShouldReleaseOwnedLock() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID postId = uuid(99);
        HotFeedProjectionGuard.ProjectionAttempt attempt = HotFeedProjectionGuard.ProjectionAttempt.accepted(
                postId,
                "evt-1",
                42L,
                "token-1"
        );
        RedisHotFeedProjectionGuard guard = new RedisHotFeedProjectionGuard(redisTemplate);

        guard.abort(attempt);

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("post:feed:hot:projection:lock:" + postId)),
                eq("token-1")
        );
    }

    @SuppressWarnings("unchecked")
    private RedisScript<Long> script(String fieldName) throws Exception {
        Field field = RedisHotFeedProjectionGuard.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (RedisScript<Long>) field.get(null);
    }
}
