package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.social.domain.model.FollowRelation;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisFollowRepositoryTest {

    @Test
    void redisRepositoryShouldOverrideFilteredFollowApis() throws Exception {
        assertDeclared("countFolloweesExcludingBlocked", UUID.class, int.class, BlockRepository.class);
        assertDeclared("countFollowersExcludingBlocked", int.class, UUID.class, BlockRepository.class);
        assertDeclared("listFolloweesExcludingBlocked", UUID.class, int.class, BlockRepository.class, int.class, int.class);
        assertDeclared("listFollowersExcludingBlocked", int.class, UUID.class, BlockRepository.class, int.class, int.class);
        assertDeclared("listFolloweeIdsExcludingBlocked", UUID.class, int.class, BlockRepository.class, int.class);
    }

    @Test
    void listFolloweeIdsExcludingBlockedShouldUseBoundedFilteredPath() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        BlockRepository blockRepository = mock(BlockRepository.class);
        UUID viewerId = UUID.randomUUID();
        UUID blockedUserId = UUID.randomUUID();
        UUID visibleUserId = UUID.randomUUID();

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeWithScores("followee:" + viewerId + ":3", 0, 99))
                .thenReturn(tuples(
                        tuple(blockedUserId, Instant.parse("2026-07-06T10:00:00Z")),
                        tuple(visibleUserId, Instant.parse("2026-07-06T09:00:00Z"))
                ));
        when(blockRepository.hasBlocked(viewerId, blockedUserId)).thenReturn(true);
        when(blockRepository.hasBlocked(blockedUserId, viewerId)).thenReturn(false);
        when(blockRepository.hasBlocked(viewerId, visibleUserId)).thenReturn(false);
        when(blockRepository.hasBlocked(visibleUserId, viewerId)).thenReturn(false);

        RedisFollowRepository repository = new RedisFollowRepository(redisTemplate);

        List<UUID> result = repository.listFolloweeIdsExcludingBlocked(viewerId, 3, blockRepository, 1);

        assertThat(result).containsExactly(visibleUserId);
        verify(zSetOperations).reverseRangeWithScores("followee:" + viewerId + ":3", 0, 99);
    }

    @Test
    void filteredFollowApisShouldNotHaveHardScanCap() {
        assertThat(RedisFollowRepository.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("FILTER_SCAN_MAX_ITEMS");
    }

    @Test
    void followScriptShouldNotRepairHistoricalPartialWrites() throws Exception {
        Field script = RedisFollowRepository.class.getDeclaredField("FOLLOW_SCRIPT");
        script.setAccessible(true);

        DefaultRedisScript<?> value = (DefaultRedisScript<?>) script.get(null);

        assertThat(value.getScriptAsString())
                .doesNotContain("修复历史")
                .doesNotContain("ZADD', followerKey, followeeScore")
                .doesNotContain("ZADD', followeeKey, followerScore");
    }

    private void assertDeclared(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = RedisFollowRepository.class.getDeclaredMethod(methodName, parameterTypes);
        assertThat(method.getDeclaringClass()).isEqualTo(RedisFollowRepository.class);
    }

    private Set<ZSetOperations.TypedTuple<String>> tuples(ZSetOperations.TypedTuple<String>... tuples) {
        return new LinkedHashSet<>(List.of(tuples));
    }

    private ZSetOperations.TypedTuple<String> tuple(UUID userId, Instant followTime) {
        return new ZSetOperations.TypedTuple<>() {
            @Override
            public String getValue() {
                return userId.toString();
            }

            @Override
            public Double getScore() {
                return (double) followTime.toEpochMilli();
            }

            @Override
            public int compareTo(ZSetOperations.TypedTuple<String> other) {
                return 0;
            }
        };
    }
}
