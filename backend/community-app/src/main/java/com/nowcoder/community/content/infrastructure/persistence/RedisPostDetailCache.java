package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.content.application.CacheTtlPolicy;
import com.nowcoder.community.content.application.ContentHotPathProperties;
import com.nowcoder.community.content.application.PostDetailCache;
import com.nowcoder.community.content.application.result.PostDetailResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "content.storage", havingValue = "redis", matchIfMissing = true)
public class RedisPostDetailCache implements PostDetailCache {

    private static final String DETAIL_KEY = "post:detail:";
    private static final String TERMINAL_KEY_PREFIX = "post:detail:terminal:";
    private static final DefaultRedisScript<Long> PUT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then
              redis.call('DEL', KEYS[1])
              return 0
            end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> TERMINAL_EVICT_SCRIPT = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[2], '1')
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final JsonCodec jsonCodec;
    private final CacheTtlPolicy ttlPolicy;
    private final ContentHotPathProperties hotPathProperties;

    public RedisPostDetailCache(StringRedisTemplate redisTemplate, JsonCodec jsonCodec) {
        this(redisTemplate, jsonCodec, new CacheTtlPolicy(new ContentHotPathProperties()), new ContentHotPathProperties());
    }

    @Autowired
    public RedisPostDetailCache(
            StringRedisTemplate redisTemplate,
            JsonCodec jsonCodec,
            CacheTtlPolicy ttlPolicy,
            ContentHotPathProperties hotPathProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonCodec = jsonCodec;
        this.ttlPolicy = ttlPolicy == null ? new CacheTtlPolicy(new ContentHotPathProperties()) : ttlPolicy;
        this.hotPathProperties = hotPathProperties == null ? new ContentHotPathProperties() : hotPathProperties;
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
        String key = key(postId);
        redisTemplate.execute(
                PUT_SCRIPT,
                List.of(key, terminalKey(key)),
                jsonCodec.toJson(detail),
                Long.toString(Math.max(
                        1L,
                        ttlPolicy.jitteredTtl(
                                key,
                                hotPathProperties.getCache().detailTtl()
                        ).toMillis()
                ))
        );
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

    @Override
    public void terminalEvict(UUID postId) {
        if (postId == null) {
            return;
        }
        String key = key(postId);
        Long evicted = redisTemplate.execute(
                TERMINAL_EVICT_SCRIPT,
                List.of(key, terminalKey(key))
        );
        if (!Long.valueOf(1L).equals(evicted)) {
            throw new IllegalStateException("post detail terminal fence was not persisted: postId=" + postId);
        }
    }

    private String key(UUID postId) {
        return DETAIL_KEY + postId;
    }

    private String terminalKey(String cacheKey) {
        return TERMINAL_KEY_PREFIX + "{" + cacheKey + "}";
    }
}
