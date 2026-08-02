package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.PostCounterCache;
import com.nowcoder.community.content.domain.model.PostCounterSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "content.storage", havingValue = "redis", matchIfMissing = true)
public class RedisPostCounterCache implements PostCounterCache {

    // The existing dirty key is also the hash tag, keeping both Lua keys in one Cluster slot.
    private static final String COUNTER_KEY_PREFIX = "post:counter:{post:counter:dirty}:";
    private static final String LEGACY_COUNTER_KEY_PREFIX = "post:counter:";
    private static final String VIEWER_KEY_PREFIX = "post:viewer:";
    private static final String DIRTY_KEY = "post:counter:dirty";
    private static final String DIRTY_SEQUENCE_KEY = "post:counter:{post:counter:dirty}:sequence";
    private static final String FIELD_VIEW = "viewCount";
    private static final String FIELD_LIKE = "likeCount";
    private static final String FIELD_COMMENT = "commentCount";
    private static final String FIELD_BOOKMARK = "bookmarkCount";
    private static final String FIELD_SCORE = "score";

    private static final DefaultRedisScript<Long> UPDATE_COUNTER_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('HINCRBY', KEYS[1], ARGV[1], ARGV[2])
            local revision = redis.call('INCR', KEYS[3])
            redis.call('ZADD', KEYS[2], revision, ARGV[3])
            return 1
            """,
            Long.class
    );

    private static final DefaultRedisScript<Long> UPDATE_SCORE_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
            local revision = redis.call('INCR', KEYS[3])
            redis.call('ZADD', KEYS[2], revision, ARGV[3])
            return 1
            """,
            Long.class
    );

    private static final DefaultRedisScript<Long> MARK_VIEWER_SEEN_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('SETNX', KEYS[1], ARGV[1]) == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[2])
              return 1
            end
            return 0
            """,
            Long.class
    );

    private static final DefaultRedisScript<Long> CLEAR_DIRTY_SCRIPT = new DefaultRedisScript<>(
            """
            local removed = 0
            for index = 1, #ARGV, 2 do
              local current = redis.call('ZSCORE', KEYS[1], ARGV[index])
              if current ~= false and tonumber(current) == tonumber(ARGV[index + 1]) then
                removed = removed + redis.call('ZREM', KEYS[1], ARGV[index])
              end
            end
            return removed
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final Duration viewerWindow;

    public RedisPostCounterCache(
            StringRedisTemplate redisTemplate,
            @Value("${content.counter.viewer-window-seconds:86400}") long viewerWindowSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.viewerWindow = Duration.ofSeconds(Math.max(60L, viewerWindowSeconds));
    }

    @Override
    public PostCounterSnapshot get(UUID postId) {
        if (postId == null) {
            return new PostCounterSnapshot(null, 0L, 0L, 0L, 0L, 0.0);
        }
        String counterKey = counterKey(postId);
        String legacyCounterKey = legacyCounterKey(postId);
        Map<Object, Object> values = entries(counterKey);
        Map<Object, Object> legacyValues = entries(legacyCounterKey);
        if (values.isEmpty() && legacyValues.isEmpty()) {
            return new PostCounterSnapshot(postId, 0L, 0L, 0L, 0L, 0.0);
        }
        List<Object> invalidFields = new ArrayList<>();
        List<Object> invalidLegacyFields = new ArrayList<>();
        long viewCount = addCounts(
                longValue(legacyValues.get(FIELD_VIEW), FIELD_VIEW, invalidLegacyFields),
                longValue(values.get(FIELD_VIEW), FIELD_VIEW, invalidFields)
        );
        long likeCount = addCounts(
                longValue(legacyValues.get(FIELD_LIKE), FIELD_LIKE, invalidLegacyFields),
                longValue(values.get(FIELD_LIKE), FIELD_LIKE, invalidFields)
        );
        long commentCount = addCounts(
                longValue(legacyValues.get(FIELD_COMMENT), FIELD_COMMENT, invalidLegacyFields),
                longValue(values.get(FIELD_COMMENT), FIELD_COMMENT, invalidFields)
        );
        long bookmarkCount = addCounts(
                longValue(legacyValues.get(FIELD_BOOKMARK), FIELD_BOOKMARK, invalidLegacyFields),
                longValue(values.get(FIELD_BOOKMARK), FIELD_BOOKMARK, invalidFields)
        );
        double score = values.containsKey(FIELD_SCORE)
                ? doubleValue(values.get(FIELD_SCORE), FIELD_SCORE, invalidFields)
                : doubleValue(legacyValues.get(FIELD_SCORE), FIELD_SCORE, invalidLegacyFields);
        deleteInvalidFields(counterKey, invalidFields);
        deleteInvalidFields(legacyCounterKey, invalidLegacyFields);
        return new PostCounterSnapshot(
                postId,
                viewCount,
                likeCount,
                commentCount,
                bookmarkCount,
                score
        );
    }

    @Override
    public boolean markViewerSeen(UUID postId, String viewerKey, Instant viewedAt) {
        if (postId == null || !StringUtils.hasText(viewerKey)) {
            return false;
        }
        Instant instant = viewedAt == null ? Instant.now() : viewedAt;
        Long result = redisTemplate.execute(
                MARK_VIEWER_SEEN_SCRIPT,
                List.of(viewerKey(postId, viewerKey)),
                Long.toString(instant.toEpochMilli()),
                Long.toString(viewerWindow.toMillis())
        );
        return result != null && result > 0;
    }

    @Override
    public void incrementViewCount(UUID postId) {
        incrementCounter(postId, FIELD_VIEW, 1L);
    }

    public void incrementLikeCount(UUID postId) {
        incrementLikeCount(postId, 1L);
    }

    @Override
    public void incrementLikeCount(UUID postId, long delta) {
        incrementCounter(postId, FIELD_LIKE, delta);
    }

    public void incrementCommentCount(UUID postId) {
        incrementCommentCount(postId, 1L);
    }

    @Override
    public void incrementCommentCount(UUID postId, long delta) {
        incrementCounter(postId, FIELD_COMMENT, delta);
    }

    public void incrementBookmarkCount(UUID postId) {
        incrementBookmarkCount(postId, 1L);
    }

    @Override
    public void incrementBookmarkCount(UUID postId, long delta) {
        incrementCounter(postId, FIELD_BOOKMARK, delta);
    }

    @Override
    public void updateScore(UUID postId, double score) {
        if (postId == null) {
            return;
        }
        redisTemplate.execute(
                UPDATE_SCORE_SCRIPT,
                List.of(counterKey(postId), DIRTY_KEY, DIRTY_SEQUENCE_KEY),
                FIELD_SCORE,
                Double.toString(score),
                postId.toString()
        );
    }

    @Override
    public List<DirtyPost> dirtyPosts(int limit) {
        int size = Math.max(1, limit);
        LinkedHashSet<DirtyPost> ordered = new LinkedHashSet<>();
        var tuples = redisTemplate.opsForZSet().rangeWithScores(DIRTY_KEY, 0, size - 1L);
        if (tuples == null) {
            return List.of();
        }
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            UUID postId = tuple == null ? null : parseUuid(tuple.getValue());
            Double score = tuple == null ? null : tuple.getScore();
            if (postId != null && score != null && score > 0.0 && score <= Long.MAX_VALUE) {
                ordered.add(new DirtyPost(postId, score.longValue()));
            }
        }
        return new ArrayList<>(ordered);
    }

    @Override
    public void clearDirtyPosts(List<DirtyPost> dirtyPosts) {
        if (dirtyPosts == null || dirtyPosts.isEmpty()) {
            return;
        }
        List<String> args = new ArrayList<>();
        for (DirtyPost dirtyPost : dirtyPosts) {
            if (dirtyPost != null && dirtyPost.postId() != null && dirtyPost.revision() > 0L) {
                args.add(dirtyPost.postId().toString());
                args.add(Long.toString(dirtyPost.revision()));
            }
        }
        if (!args.isEmpty()) {
            redisTemplate.execute(CLEAR_DIRTY_SCRIPT, List.of(DIRTY_KEY), args.toArray());
        }
    }

    private void incrementCounter(UUID postId, String field, long delta) {
        if (postId == null || delta == 0L) {
            return;
        }
        redisTemplate.execute(
                UPDATE_COUNTER_SCRIPT,
                List.of(counterKey(postId), DIRTY_KEY, DIRTY_SEQUENCE_KEY),
                field,
                Long.toString(delta),
                postId.toString()
        );
    }

    private Map<Object, Object> entries(String key) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(key);
        return values == null ? Map.of() : values;
    }

    private void deleteInvalidFields(String key, List<Object> invalidFields) {
        if (!invalidFields.isEmpty()) {
            redisTemplate.opsForHash().delete(key, invalidFields.toArray());
        }
    }

    private static long addCounts(long baseline, long overlay) {
        try {
            return Math.addExact(baseline, overlay);
        } catch (ArithmeticException ex) {
            return overlay >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private static long longValue(Object raw, String field, List<Object> invalidFields) {
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException ex) {
            if (field != null && invalidFields != null) {
                invalidFields.add(field);
            }
            return 0L;
        }
    }

    private static double doubleValue(Object raw, String field, List<Object> invalidFields) {
        if (raw == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(raw.toString());
        } catch (NumberFormatException ex) {
            if (field != null && invalidFields != null) {
                invalidFields.add(field);
            }
            return 0.0;
        }
    }

    private static UUID parseUuid(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String counterKey(UUID postId) {
        return COUNTER_KEY_PREFIX + postId;
    }

    private static String legacyCounterKey(UUID postId) {
        return LEGACY_COUNTER_KEY_PREFIX + postId;
    }

    private static String viewerKey(UUID postId, String viewerKey) {
        return VIEWER_KEY_PREFIX + postId + ":" + viewerKey.trim();
    }
}
