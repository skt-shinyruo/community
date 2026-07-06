package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.content.application.PostDetailCache;
import com.nowcoder.community.content.application.result.PostDetailResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "content.storage", havingValue = "redis", matchIfMissing = true)
public class RedisPostDetailCache implements PostDetailCache {

    private static final String DETAIL_KEY = "post:detail:";

    private final StringRedisTemplate redisTemplate;
    private final JsonCodec jsonCodec;

    public RedisPostDetailCache(StringRedisTemplate redisTemplate, JsonCodec jsonCodec) {
        this.redisTemplate = redisTemplate;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public PostDetailResult get(UUID postId) {
        if (postId == null) {
            return null;
        }
        String raw = redisTemplate.opsForValue().get(key(postId));
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return jsonCodec.fromJson(raw, PostDetailResult.class);
        } catch (JsonCodecException ex) {
            evict(postId);
            return null;
        }
    }

    @Override
    public void put(UUID postId, PostDetailResult detail) {
        if (postId == null || detail == null) {
            return;
        }
        redisTemplate.opsForValue().set(key(postId), jsonCodec.toJson(detail));
    }

    @Override
    public void evict(UUID postId) {
        if (postId == null) {
            return;
        }
        try {
            redisTemplate.delete(key(postId));
        } catch (RuntimeException ignored) {
            // best-effort cleanup
        }
    }

    private String key(UUID postId) {
        return DETAIL_KEY + postId;
    }
}
