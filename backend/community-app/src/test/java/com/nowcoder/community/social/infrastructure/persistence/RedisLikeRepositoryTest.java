package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.social.domain.model.LikeRelation;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisLikeRepositoryTest {

    @Test
    void incrementUserLikeCountShouldUseAtomicClampScript() throws Exception {
        Field field = RedisLikeRepository.class.getDeclaredField("INCREMENT_USER_LIKE_COUNT_SCRIPT");
        field.setAccessible(true);

        String script = String.valueOf(field.get(null));

        assertThat(script).contains("INCRBY");
        assertThat(script).contains("< 0");
        assertThat(script).contains("SET");
    }

    @Test
    @SuppressWarnings("unchecked")
    void scanLikesByEntityShouldReturnDeterministicActorCursorPageWithOwners() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        UUID entityId = uuid(100);
        UUID actor1 = uuid(1);
        UUID actor2 = uuid(2);
        UUID actor3 = uuid(3);
        UUID owner2 = uuid(20);
        UUID owner3 = uuid(30);
        String entityKey = "like:entity:1:" + entityId;
        String ownerKey = "like:entity-owner:1:" + entityId;
        when(setOperations.members(entityKey)).thenReturn(new LinkedHashSet<>(List.of(
                actor3.toString(),
                actor1.toString(),
                actor2.toString()
        )));
        when(hashOperations.multiGet(eq(ownerKey), eq(List.of(actor2.toString(), actor3.toString()))))
                .thenReturn(List.of(owner2.toString(), owner3.toString()));

        List<LikeRelation> page = new RedisLikeRepository(redisTemplate)
                .scanLikesByEntity(1, entityId, actor1, 2);

        assertThat(page).containsExactly(
                new LikeRelation(actor2, 1, entityId, owner2),
                new LikeRelation(actor3, 1, entityId, owner3)
        );
    }

    private UUID uuid(long value) {
        return new UUID(0L, value);
    }
}
