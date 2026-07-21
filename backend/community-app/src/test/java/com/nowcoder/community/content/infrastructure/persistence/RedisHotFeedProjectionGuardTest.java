package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.HotFeedProjectionGuard;
import io.lettuce.core.cluster.SlotHash;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Testcontainers
class RedisHotFeedProjectionGuardTest {

    private static final int REDIS_PORT = 6379;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);

    @Test
    void tryBeginShouldAcquirePerPostLockWithAtomicScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID postId = uuid(99);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(allKeys(postId, "evt-1")),
                eq("42"),
                eq("0"),
                anyString(),
                eq("30000")
        )).thenReturn(1L);
        RedisHotFeedProjectionGuard guard = new RedisHotFeedProjectionGuard(redisTemplate);

        HotFeedProjectionGuard.ProjectionAttempt attempt = guard.tryBegin(postId, "evt-1", 42L, false);

        assertThat(attempt.accepted()).isTrue();
        assertThat(attempt.terminalDeletion()).isFalse();
        assertThat(attempt.token()).isNotBlank();
    }

    @Test
    void tryBeginShouldRejectBlankEventIdWithoutRedisCall() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisHotFeedProjectionGuard guard = new RedisHotFeedProjectionGuard(redisTemplate);

        assertThat(guard.tryBegin(uuid(99), " ", 42L, false).accepted()).isFalse();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void tryBeginShouldRejectDuplicateOrStaleEventsFromScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID postId = uuid(99);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(allKeys(postId, "evt-1")),
                eq("42"),
                eq("0"),
                anyString(),
                eq("30000")
        )).thenReturn(-1L);
        RedisHotFeedProjectionGuard guard = new RedisHotFeedProjectionGuard(redisTemplate);

        assertThat(guard.tryBegin(postId, "evt-1", 42L, false).accepted()).isFalse();
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
    void allLuaKeysForAPostShouldShareOneRedisClusterSlot() {
        UUID postId = uuid(99);
        List<String> keys = allKeys(postId, "evt:{foreign-slot}:1");

        assertThat(keys)
                .allMatch(key -> key.contains("{" + postId + "}"))
                .extracting(key -> SlotHash.getSlot((String) key))
                .containsOnly(SlotHash.getSlot(lockKey(postId)));
    }

    @Test
    void isCurrentShouldCheckLeaseTokenTombstoneAndCommittedVersion() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID postId = uuid(99);
        HotFeedProjectionGuard.ProjectionAttempt attempt = HotFeedProjectionGuard.ProjectionAttempt.accepted(
                postId,
                "evt-1",
                42L,
                false,
                "token-1"
        );
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(lockKey(postId), versionKey(postId), tombstoneKey(postId))),
                eq("token-1"),
                eq("42"),
                eq("0")
        )).thenReturn(1L);
        RedisHotFeedProjectionGuard guard = new RedisHotFeedProjectionGuard(redisTemplate);

        assertThat(guard.isCurrent(attempt)).isTrue();

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(lockKey(postId), versionKey(postId), tombstoneKey(postId))),
                eq("token-1"),
                eq("42"),
                eq("0")
        );
    }

    @Test
    void isCurrentShouldFailForRetryWhenLeaseTokenWasLost() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID postId = uuid(98);
        HotFeedProjectionGuard.ProjectionAttempt attempt = HotFeedProjectionGuard.ProjectionAttempt.accepted(
                postId,
                "evt-delete",
                5L,
                true,
                "expired-token"
        );
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(lockKey(postId), versionKey(postId), tombstoneKey(postId))),
                eq("expired-token"),
                eq("5"),
                eq("1")
        )).thenReturn(-2L);
        RedisHotFeedProjectionGuard guard = new RedisHotFeedProjectionGuard(redisTemplate);

        assertThatThrownBy(() -> guard.isCurrent(attempt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hot feed projection current check lost lease")
                .hasMessageContaining(postId.toString());
    }

    @Test
    void commitShouldMarkEventKeepMaximumVersionAndPermanentlyTombstoneDeletion() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID postId = uuid(99);
        HotFeedProjectionGuard.ProjectionAttempt attempt = HotFeedProjectionGuard.ProjectionAttempt.accepted(
                postId,
                "evt-delete",
                5L,
                true,
                "token-1"
        );
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(allKeys(postId, "evt-delete")),
                eq("token-1"),
                eq("604800"),
                eq("5"),
                eq("1")
        )).thenReturn(1L);
        RedisHotFeedProjectionGuard guard = new RedisHotFeedProjectionGuard(redisTemplate);

        guard.commit(attempt);

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(allKeys(postId, "evt-delete")),
                eq("token-1"),
                eq("604800"),
                eq("5"),
                eq("1")
        );
    }

    @Test
    void terminalDeletionShouldDominateEveryOrdinaryVersionInRealRedis() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        UUID postId = UUID.randomUUID();
        RedisHotFeedProjectionGuard guard = new RedisHotFeedProjectionGuard(redisTemplate);
        try {
            HotFeedProjectionGuard.ProjectionAttempt ordinary = guard.tryBegin(postId, "evt-normal-10", 10L, false);
            assertThat(ordinary.accepted()).isTrue();
            assertThat(guard.isCurrent(ordinary)).isTrue();
            guard.commit(ordinary);

            HotFeedProjectionGuard.ProjectionAttempt deletion = guard.tryBegin(postId, "evt-delete-5", 5L, true);
            assertThat(deletion.accepted()).isTrue();
            assertThat(guard.isCurrent(deletion)).isTrue();
            guard.commit(deletion);

            assertThat(redisTemplate.opsForValue().get(versionKey(postId))).isEqualTo("10");
            assertThat(redisTemplate.opsForValue().get(tombstoneKey(postId))).isEqualTo("1");
            assertThat(redisTemplate.getExpire(tombstoneKey(postId), TimeUnit.SECONDS)).isEqualTo(-1L);
            assertThat(redisTemplate.getExpire(eventKey(postId, "evt-delete-5"), TimeUnit.SECONDS))
                    .isBetween(1L, 604800L);
            assertThat(guard.tryBegin(postId, "evt-normal-4", 4L, false).accepted()).isFalse();
            assertThat(guard.tryBegin(postId, "evt-normal-5", 5L, false).accepted()).isFalse();
            assertThat(guard.tryBegin(postId, "evt-normal-11", 11L, false).accepted()).isFalse();
            assertThat(guard.tryBegin(postId, "evt-delete-5", 5L, true).accepted()).isFalse();

            guard.abort(deletion);

            assertThat(redisTemplate.opsForValue().get(tombstoneKey(postId))).isEqualTo("1");
            assertThat(redisTemplate.getExpire(tombstoneKey(postId), TimeUnit.SECONDS)).isEqualTo(-1L);
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void abortAndUnownedCommitShouldNeverCreateTerminalStateInRealRedis() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        UUID postId = UUID.randomUUID();
        RedisHotFeedProjectionGuard guard = new RedisHotFeedProjectionGuard(redisTemplate);
        try {
            HotFeedProjectionGuard.ProjectionAttempt deletion = guard.tryBegin(postId, "evt-delete-abort", 5L, true);
            assertThatThrownBy(() -> guard.commit(HotFeedProjectionGuard.ProjectionAttempt.accepted(
                            postId,
                            "evt-delete-abort",
                            5L,
                            true,
                            "not-the-owner"
                    )))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("hot feed projection commit lost lease")
                    .hasMessageContaining(postId.toString());

            assertThat(redisTemplate.hasKey(eventKey(postId, "evt-delete-abort"))).isFalse();
            assertThat(redisTemplate.hasKey(versionKey(postId))).isFalse();
            assertThat(redisTemplate.hasKey(tombstoneKey(postId))).isFalse();

            guard.abort(deletion);

            assertThat(redisTemplate.hasKey(lockKey(postId))).isFalse();
            assertThat(redisTemplate.hasKey(eventKey(postId, "evt-delete-abort"))).isFalse();
            assertThat(redisTemplate.hasKey(tombstoneKey(postId))).isFalse();
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void abortShouldReleaseOwnedLockOnly() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID postId = uuid(99);
        HotFeedProjectionGuard.ProjectionAttempt attempt = HotFeedProjectionGuard.ProjectionAttempt.accepted(
                postId,
                "evt-1",
                42L,
                true,
                "token-1"
        );
        RedisHotFeedProjectionGuard guard = new RedisHotFeedProjectionGuard(redisTemplate);

        guard.abort(attempt);

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(lockKey(postId))),
                eq("token-1")
        );
    }

    private static List<String> allKeys(UUID postId, String sourceEventId) {
        return List.of(
                lockKey(postId),
                eventKey(postId, sourceEventId),
                versionKey(postId),
                tombstoneKey(postId)
        );
    }

    private static String lockKey(UUID postId) {
        return "post:feed:hot:projection:lock:{" + postId + "}";
    }

    private static String eventKey(UUID postId, String sourceEventId) {
        return "post:feed:hot:projection:event:{" + postId + "}:" + sourceEventId;
    }

    private static String versionKey(UUID postId) {
        return "post:feed:hot:projection:version:{" + postId + "}";
    }

    private static String tombstoneKey(UUID postId) {
        return "post:feed:hot:projection:tombstone:{" + postId + "}";
    }

    @SuppressWarnings("unchecked")
    private RedisScript<Long> script(String fieldName) throws Exception {
        Field field = RedisHotFeedProjectionGuard.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (RedisScript<Long>) field.get(null);
    }
}
