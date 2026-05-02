package com.nowcoder.community.social.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

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
}
