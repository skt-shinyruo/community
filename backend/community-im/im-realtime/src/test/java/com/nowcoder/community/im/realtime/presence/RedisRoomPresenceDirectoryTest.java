package com.nowcoder.community.im.realtime.presence;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRoomPresenceDirectoryTest {

    private static final long NOW_MILLIS = 1_783_728_000_000L;

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
    private final RoomPresenceProperties properties = new RoomPresenceProperties();
    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(NOW_MILLIS), ZoneOffset.UTC);

    RedisRoomPresenceDirectoryTest() {
        properties.setKeyPrefix("im:");
        properties.setTtl(Duration.ofSeconds(30));
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    void activateUpsertsNormalizedWorkerWithLeaseExpiryScore() {
        UUID roomId = uuid(1);
        String key = "im:room:" + roomId + ":workers";

        directory().activate(roomId, " worker-a ");

        verify(zSetOperations).add(key, "worker-a", NOW_MILLIS + Duration.ofSeconds(30).toMillis());
        verify(redisTemplate, never()).opsForSet();
        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void deactivateRemovesNormalizedWorkerFromLeaseSet() {
        UUID roomId = uuid(2);
        String key = "im:room:" + roomId + ":workers";

        directory().deactivate(roomId, " worker-a ");

        verify(zSetOperations).remove(key, "worker-a");
        verify(redisTemplate, never()).opsForSet();
        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void activeWorkerIdsPurgesExpiredScoresBeforeReadingAndNormalizesMembers() {
        UUID roomId = uuid(3);
        String key = "im:room:" + roomId + ":workers";
        when(zSetOperations.rangeByScore(key, NOW_MILLIS, Double.POSITIVE_INFINITY))
                .thenReturn(new LinkedHashSet<>(Set.of(" worker-a ", "worker-b", "  ")));

        Set<String> activeWorkerIds = directory().activeWorkerIds(roomId);

        assertThat(activeWorkerIds).containsExactlyInAnyOrder("worker-a", "worker-b");
        var order = inOrder(zSetOperations);
        order.verify(zSetOperations).removeRangeByScore(key, Double.NEGATIVE_INFINITY, NOW_MILLIS);
        order.verify(zSetOperations).rangeByScore(key, NOW_MILLIS, Double.POSITIVE_INFINITY);
        verify(redisTemplate, never()).opsForSet();
        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void redisErrorsPropagateToCaller() {
        UUID roomId = uuid(4);
        String key = "im:room:" + roomId + ":workers";
        RuntimeException failure = new RuntimeException("redis unavailable");
        when(zSetOperations.add(key, "worker-a", NOW_MILLIS + Duration.ofSeconds(30).toMillis()))
                .thenThrow(failure);

        assertThatThrownBy(() -> directory().activate(roomId, "worker-a"))
                .isSameAs(failure);
    }

    @Test
    void leaseExpiryOverflowFailsBeforeWritingRedis() {
        Clock overflowClock = Clock.fixed(Instant.ofEpochMilli(Long.MAX_VALUE - 1), ZoneOffset.UTC);

        assertThatThrownBy(() -> new RedisRoomPresenceDirectory(redisTemplate, properties, overflowClock)
                .activate(uuid(5), "worker-a"))
                .isInstanceOf(ArithmeticException.class);

        verify(zSetOperations, never()).add(anyString(), anyString(), org.mockito.ArgumentMatchers.anyDouble());
    }

    private RedisRoomPresenceDirectory directory() {
        return new RedisRoomPresenceDirectory(redisTemplate, properties, clock);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
