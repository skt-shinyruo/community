package com.nowcoder.community.social.block;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RedisBlockRepositoryTest {

    @Test
    void scanBlocksAfterShouldRejectRedisProjectionBootstrap() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisBlockRepository repository = new RedisBlockRepository(redisTemplate);

        assertThatThrownBy(() -> repository.scanBlocksAfter(null, null, 10))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("social.storage=db");
    }
}
