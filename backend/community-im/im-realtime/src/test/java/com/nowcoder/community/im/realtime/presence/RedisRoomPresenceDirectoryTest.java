package com.nowcoder.community.im.realtime.presence;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRoomPresenceDirectoryTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final SetOperations<String, String> setOperations = mock(SetOperations.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final RoomPresenceProperties properties = new RoomPresenceProperties();

    RedisRoomPresenceDirectoryTest() {
        properties.setKeyPrefix("im:");
        properties.setTtl(Duration.ofSeconds(30));
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void activateAddsWorkerToRoomSetAndRefreshesLivenessTtl() {
        UUID roomId = uuid(1);

        directory().activate(roomId, " worker-a ");

        verify(setOperations).add("im:room:" + roomId + ":workers", "worker-a");
        verify(valueOperations).set("im:room:" + roomId + ":worker:worker-a", "1", Duration.ofSeconds(30));
    }

    @Test
    void activeWorkerIdsReturnsOnlyWorkersWithLiveKeysAndCleansStaleMembers() {
        UUID roomId = uuid(2);
        String setKey = "im:room:" + roomId + ":workers";
        when(setOperations.members(setKey)).thenReturn(new LinkedHashSet<>(Set.of(
                " worker-a ",
                "worker-stale",
                "",
                "worker-b"
        )));
        when(redisTemplate.hasKey("im:room:" + roomId + ":worker:worker-a")).thenReturn(true);
        when(redisTemplate.hasKey("im:room:" + roomId + ":worker:worker-stale")).thenReturn(false);
        when(redisTemplate.hasKey("im:room:" + roomId + ":worker:worker-b")).thenReturn(true);

        Set<String> activeWorkerIds = directory().activeWorkerIds(roomId);

        assertThat(activeWorkerIds).containsExactlyInAnyOrder("worker-a", "worker-b");
        verify(setOperations).remove(setKey, "worker-stale");
    }

    @Test
    void deactivateRemovesWorkerFromRoomSetAndDeletesLivenessKey() {
        UUID roomId = uuid(3);

        directory().deactivate(roomId, " worker-a ");

        verify(setOperations).remove("im:room:" + roomId + ":workers", "worker-a");
        verify(redisTemplate).delete("im:room:" + roomId + ":worker:worker-a");
    }

    private RedisRoomPresenceDirectory directory() {
        return new RedisRoomPresenceDirectory(redisTemplate, properties);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
