package com.nowcoder.community.social.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LikeMapperTest {

    @Test
    void incrementUserLikeCountSqlShouldClampAbsentAndUpdatedRowsAtZero() throws Exception {
        Method method = LikeMapper.class.getMethod("incrementUserLikeCount", UUID.class, long.class);

        String sql = String.join(" ", method.getAnnotation(Insert.class).value()).toLowerCase();

        assertThat(sql).contains("values(#{userid}, greatest(0, #{delta}))");
        assertThat(sql).contains("like_count = greatest(0, like_count + #{delta})");
    }
}
