package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.content.application.CommentPageCache;
import com.nowcoder.community.content.application.result.CommentPageResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "content.storage", havingValue = "redis", matchIfMissing = true)
public class RedisCommentPageCache implements CommentPageCache {

    private static final String ROOT_PAGE_KEY_PREFIX = "comment:root-page:";

    private final StringRedisTemplate redisTemplate;
    private final JsonCodec jsonCodec;
    private final Duration ttl;

    public RedisCommentPageCache(
            StringRedisTemplate redisTemplate,
            JsonCodec jsonCodec,
            @Value("${content.comments.first-page-cache-ttl-seconds:15}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonCodec = jsonCodec;
        this.ttl = Duration.ofSeconds(Math.max(1L, ttlSeconds));
    }

    @Override
    public CommentPageResult getRootPage(UUID postId, String cursor, int size) {
        if (postId == null) {
            return null;
        }
        String key = pageKey(postId, cursor, size);
        String raw = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return jsonCodec.fromJson(raw, CommentPageResult.class);
        } catch (JsonCodecException ex) {
            redisTemplate.delete(key);
            return null;
        }
    }

    @Override
    public void putRootPage(UUID postId, String cursor, int size, CommentPageResult result) {
        if (postId == null || result == null) {
            return;
        }
        String key = pageKey(postId, cursor, size);
        String indexKey = indexKey(postId);
        redisTemplate.opsForValue().set(key, jsonCodec.toJson(result), ttl);
        redisTemplate.opsForSet().add(indexKey, key);
        redisTemplate.expire(indexKey, ttl);
    }

    @Override
    public void evictPost(UUID postId) {
        if (postId == null) {
            return;
        }
        String indexKey = indexKey(postId);
        Set<String> keys = new LinkedHashSet<>();
        Set<String> pageKeys = redisTemplate.opsForSet().members(indexKey);
        if (pageKeys != null) {
            for (String pageKey : pageKeys) {
                if (StringUtils.hasText(pageKey)) {
                    keys.add(pageKey);
                }
            }
        }
        keys.add(indexKey);
        redisTemplate.delete(keys);
    }

    private static String pageKey(UUID postId, String cursor, int size) {
        return ROOT_PAGE_KEY_PREFIX
                + postId
                + ":cursor:"
                + (StringUtils.hasText(cursor) ? cursor.trim() : "initial")
                + ":size:"
                + Math.max(1, size);
    }

    private static String indexKey(UUID postId) {
        return ROOT_PAGE_KEY_PREFIX + postId + ":keys";
    }
}
