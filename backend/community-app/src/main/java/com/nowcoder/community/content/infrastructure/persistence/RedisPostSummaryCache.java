package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.content.application.CacheTtlPolicy;
import com.nowcoder.community.content.application.ContentHotPathProperties;
import com.nowcoder.community.content.application.PostSummaryCache;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "content.storage", havingValue = "redis", matchIfMissing = true)
public class RedisPostSummaryCache implements PostSummaryCache {

    private static final String SUMMARY_KEY = "post:summary:";
    private static final String TERMINAL_KEY_PREFIX = "post:summary:terminal:";
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

    public RedisPostSummaryCache(StringRedisTemplate redisTemplate, JsonCodec jsonCodec) {
        this(redisTemplate, jsonCodec, new CacheTtlPolicy(new ContentHotPathProperties()), new ContentHotPathProperties());
    }

    @Autowired
    public RedisPostSummaryCache(
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
    public Map<UUID, PostSummaryResult> getAll(List<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> orderedIds = postIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (orderedIds.isEmpty()) {
            return Map.of();
        }
        List<String> keys = orderedIds.stream().map(this::key).toList();
        List<String> rawValues = redisTemplate.opsForValue().multiGet(keys);
        if (rawValues == null || rawValues.isEmpty()) {
            return Map.of();
        }

        Map<UUID, PostSummaryResult> cached = new LinkedHashMap<>();
        List<String> poisonKeys = new ArrayList<>();
        for (int i = 0; i < orderedIds.size() && i < rawValues.size(); i++) {
            String raw = rawValues.get(i);
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            try {
                cached.put(orderedIds.get(i), jsonCodec.fromJson(raw, PostSummaryResult.class));
            } catch (JsonCodecException ex) {
                poisonKeys.add(keys.get(i));
            }
        }
        deleteKeys(poisonKeys);
        return cached;
    }

    @Override
    public void putAll(List<PostSummaryResult> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return;
        }
        for (PostSummaryResult summary : summaries) {
            if (summary == null || summary.id() == null) {
                continue;
            }
            String key = key(summary.id());
            redisTemplate.execute(
                    PUT_SCRIPT,
                    List.of(key, terminalKey(key)),
                    jsonCodec.toJson(summary),
                    Long.toString(Math.max(
                            1L,
                            ttlPolicy.jitteredTtl(
                                    key,
                                    hotPathProperties.getCache().summaryTtl()
                            ).toMillis()
                    ))
            );
        }
    }

    @Override
    public void evictAll(List<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return;
        }
        List<String> keys = postIds.stream()
                .filter(id -> id != null)
                .map(this::key)
                .distinct()
                .toList();
        deleteKeys(keys);
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
            throw new IllegalStateException("post summary terminal fence was not persisted: postId=" + postId);
        }
    }

    private void deleteKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        try {
            redisTemplate.delete(new LinkedHashSet<>(keys));
        } catch (RuntimeException ignored) {
            // best-effort cleanup for invalid cache payloads
        }
    }

    private String key(UUID postId) {
        return SUMMARY_KEY + postId;
    }

    private String terminalKey(String cacheKey) {
        return TERMINAL_KEY_PREFIX + "{" + cacheKey + "}";
    }
}
