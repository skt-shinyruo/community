package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.content.application.CacheTtlPolicy;
import com.nowcoder.community.content.application.ContentHotPathProperties;
import com.nowcoder.community.content.application.PostSummaryCache;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import org.springframework.beans.factory.annotation.Autowired;
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
public class RedisPostSummaryCache implements PostSummaryCache {

    private static final String SUMMARY_KEY = "post:summary:";
    private static final String TERMINAL_KEY_PREFIX = "post:summary:terminal:";
    private static final String VERSION_KEY_PREFIX = "post:summary:version:";
    private static final String SCORE_VERSION_KEY_PREFIX = "post:summary:score-version:";
    private static final String TERMINAL_FENCE_TTL_SECONDS = "604800";
    private static final DefaultRedisScript<Long> PUT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then
              redis.call('DEL', KEYS[1])
              return 0
            end
            local minimumAggregateVersion = tonumber(redis.call('GET', KEYS[3]) or '0')
            local minimumScoreVersion = tonumber(redis.call('GET', KEYS[4]) or '0')
            local sourceAggregateVersion = tonumber(ARGV[3])
            local sourceScoreVersion = tonumber(ARGV[4])
            if sourceAggregateVersion < minimumAggregateVersion
              or (sourceAggregateVersion == minimumAggregateVersion and sourceScoreVersion < minimumScoreVersion) then
              return 0
            end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            redis.call('SET', KEYS[3], ARGV[3], 'EX', ARGV[5])
            redis.call('SET', KEYS[4], ARGV[4], 'EX', ARGV[5])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> EVICT_SCRIPT = new DefaultRedisScript<>("""
            local minimumAggregateVersion = tonumber(redis.call('GET', KEYS[2]) or '0')
            local minimumScoreVersion = tonumber(redis.call('GET', KEYS[3]) or '0')
            local nextAggregateVersion = tonumber(ARGV[1])
            local nextScoreVersion = tonumber(ARGV[2])
            if nextAggregateVersion > minimumAggregateVersion
              or (nextAggregateVersion == minimumAggregateVersion and nextScoreVersion > minimumScoreVersion) then
              minimumAggregateVersion = nextAggregateVersion
              minimumScoreVersion = nextScoreVersion
            end
            redis.call('SET', KEYS[2], tostring(minimumAggregateVersion), 'EX', ARGV[3])
            redis.call('SET', KEYS[3], tostring(minimumScoreVersion), 'EX', ARGV[3])
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> TERMINAL_EVICT_SCRIPT = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[2], '1', 'EX', ARGV[1])
            local minimumAggregateVersion = tonumber(redis.call('GET', KEYS[3]) or '0')
            local minimumScoreVersion = tonumber(redis.call('GET', KEYS[4]) or '0')
            local nextAggregateVersion = tonumber(ARGV[2])
            local nextScoreVersion = tonumber(ARGV[3])
            if nextAggregateVersion > minimumAggregateVersion
              or (nextAggregateVersion == minimumAggregateVersion and nextScoreVersion > minimumScoreVersion) then
              minimumAggregateVersion = nextAggregateVersion
              minimumScoreVersion = nextScoreVersion
            end
            redis.call('SET', KEYS[3], tostring(minimumAggregateVersion), 'EX', ARGV[1])
            redis.call('SET', KEYS[4], tostring(minimumScoreVersion), 'EX', ARGV[1])
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final JacksonJsonCodec jsonCodec;
    private final CacheTtlPolicy ttlPolicy;
    private final ContentHotPathProperties hotPathProperties;

    public RedisPostSummaryCache(StringRedisTemplate redisTemplate, JacksonJsonCodec jsonCodec) {
        this(redisTemplate, jsonCodec, new CacheTtlPolicy(new ContentHotPathProperties()), new ContentHotPathProperties());
    }

    @Autowired
    public RedisPostSummaryCache(
            StringRedisTemplate redisTemplate,
            JacksonJsonCodec jsonCodec,
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
        putVersioned(summaries.stream()
                .map(summary -> new VersionedSummary(summary, 0L, 0L))
                .toList());
    }

    @Override
    public void putVersioned(List<VersionedSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return;
        }
        for (VersionedSummary versioned : summaries) {
            if (versioned == null) {
                continue;
            }
            PostSummaryResult summary = versioned.summary();
            if (summary == null || summary.id() == null) {
                continue;
            }
            String key = key(summary.id());
            redisTemplate.execute(
                    PUT_SCRIPT,
                    List.of(key, terminalKey(key), versionKey(key), scoreVersionKey(key)),
                    jsonCodec.toJson(summary),
                    Long.toString(Math.max(
                            1L,
                            ttlPolicy.jitteredTtl(
                                    key,
                                    hotPathProperties.getCache().summaryTtl()
                            ).toMillis()
                    )),
                    Long.toString(Math.max(0L, versioned.aggregateVersion())),
                    Long.toString(Math.max(0L, versioned.scoreVersion())),
                    TERMINAL_FENCE_TTL_SECONDS
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
    public void evictAll(List<UUID> postIds, long minimumVersion) {
        evictAll(postIds, minimumVersion, 0L);
    }

    @Override
    public void evictAll(List<UUID> postIds, long minimumAggregateVersion, long minimumScoreVersion) {
        if (postIds == null || postIds.isEmpty()) {
            return;
        }
        for (UUID postId : postIds.stream().filter(id -> id != null).distinct().toList()) {
            String key = key(postId);
            redisTemplate.execute(
                    EVICT_SCRIPT,
                    List.of(key, versionKey(key), scoreVersionKey(key)),
                    Long.toString(Math.max(0L, minimumAggregateVersion)),
                    Long.toString(Math.max(0L, minimumScoreVersion)),
                    TERMINAL_FENCE_TTL_SECONDS
            );
        }
    }

    @Override
    public void terminalEvict(UUID postId) {
        terminalEvict(postId, 0L);
    }

    @Override
    public void terminalEvict(UUID postId, long minimumVersion) {
        terminalEvict(postId, minimumVersion, 0L);
    }

    @Override
    public void terminalEvict(UUID postId, long minimumAggregateVersion, long minimumScoreVersion) {
        if (postId == null) {
            return;
        }
        String key = key(postId);
        Long evicted = redisTemplate.execute(
                TERMINAL_EVICT_SCRIPT,
                List.of(key, terminalKey(key), versionKey(key), scoreVersionKey(key)),
                TERMINAL_FENCE_TTL_SECONDS,
                Long.toString(Math.max(0L, minimumAggregateVersion)),
                Long.toString(Math.max(0L, minimumScoreVersion))
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

    private String versionKey(String cacheKey) {
        return VERSION_KEY_PREFIX + "{" + cacheKey + "}";
    }

    private String scoreVersionKey(String cacheKey) {
        return SCORE_VERSION_KEY_PREFIX + "{" + cacheKey + "}";
    }
}
