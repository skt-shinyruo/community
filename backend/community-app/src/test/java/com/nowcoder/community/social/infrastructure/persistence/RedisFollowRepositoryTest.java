package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.social.domain.repository.BlockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisFollowRepositoryTest {

    @Test
    void redisRepositoryShouldOverrideFilteredFollowApis() throws Exception {
        assertDeclared("countFolloweesExcludingBlocked", UUID.class, int.class, BlockRepository.class);
        assertDeclared("countFollowersExcludingBlocked", int.class, UUID.class, BlockRepository.class);
        assertDeclared("listFolloweesExcludingBlocked", UUID.class, int.class, BlockRepository.class, int.class, int.class);
        assertDeclared("listFollowersExcludingBlocked", int.class, UUID.class, BlockRepository.class, int.class, int.class);
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
}
