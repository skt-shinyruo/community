package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.content.application.PostSummaryCache;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    private final StringRedisTemplate redisTemplate;
    private final JsonCodec jsonCodec;

    public RedisPostSummaryCache(StringRedisTemplate redisTemplate, JsonCodec jsonCodec) {
        this.redisTemplate = redisTemplate;
        this.jsonCodec = jsonCodec;
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
        Map<String, String> payloads = new LinkedHashMap<>();
        for (PostSummaryResult summary : summaries) {
            if (summary == null || summary.id() == null) {
                continue;
            }
            payloads.put(key(summary.id()), jsonCodec.toJson(summary));
        }
        if (!payloads.isEmpty()) {
            redisTemplate.opsForValue().multiSet(payloads);
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
}
