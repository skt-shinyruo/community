package com.nowcoder.community.content.score;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "content.storage", havingValue = "redis", matchIfMissing = true)
public class RedisPostScoreQueue implements PostScoreQueue {

    private final StringRedisTemplate redisTemplate;

    public RedisPostScoreQueue(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void add(int postId) {
        redisTemplate.opsForSet().add("post:score", String.valueOf(postId));
    }

    @Override
    public Integer pop() {
        String v = redisTemplate.opsForSet().pop("post:score");
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            return null;
        }
    }
}

