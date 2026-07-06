package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.PostCounterCache;
import com.nowcoder.community.content.domain.model.PostCounterSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    private static final String COUNTER_KEY_PREFIX = "post:counter:";
    private static final String VIEWER_KEY_PREFIX = "post:viewer:";
    private static final String DIRTY_KEY = "post:counter:dirty";
    private static final String FIELD_VIEW = "viewCount";
    private static final String FIELD_LIKE = "likeCount";
    private static final String FIELD_COMMENT = "commentCount";
    private static final String FIELD_BOOKMARK = "bookmarkCount";
    private static final String FIELD_SCORE = "score";

    private static final DefaultRedisScript<Long> UPDATE_COUNTER_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('HINCRBY', KEYS[1], ARGV[1], ARGV[2])
            redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4])
            return 1
            """,
            Long.class
    );

    private static final DefaultRedisScript<Long> UPDATE_SCORE_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
            redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4])
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
        Map<Object, Object> values = redisTemplate.opsForHash().entries(counterKey(postId));
        if (values == null || values.isEmpty()) {
            return new PostCounterSnapshot(postId, 0L, 0L, 0L, 0L, 0.0);
        }
        return new PostCounterSnapshot(
                postId,
                longValue(values.get(FIELD_VIEW)),
                longValue(values.get(FIELD_LIKE)),
                longValue(values.get(FIELD_COMMENT)),
                longValue(values.get(FIELD_BOOKMARK)),
                doubleValue(values.get(FIELD_SCORE))
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
                List.of(counterKey(postId), DIRTY_KEY),
                FIELD_SCORE,
                Double.toString(score),
                Double.toString(nowScore()),
                postId.toString()
        );
    }

    @Override
    public List<UUID> dirtyPostIds(int limit) {
        int size = Math.max(1, limit);
        LinkedHashSet<UUID> ordered = new LinkedHashSet<>();
        for (String rawId : redisTemplate.opsForZSet().range(DIRTY_KEY, 0, size - 1L)) {
            UUID postId = parseUuid(rawId);
            if (postId != null) {
                ordered.add(postId);
            }
        }
        return new ArrayList<>(ordered);
    }

    @Override
    public void clearDirtyPostIds(List<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return;
        }
        List<String> members = postIds.stream()
                .filter(id -> id != null)
                .map(UUID::toString)
                .toList();
        if (!members.isEmpty()) {
            redisTemplate.opsForZSet().remove(DIRTY_KEY, members.toArray(Object[]::new));
        }
    }

    private void incrementCounter(UUID postId, String field, long delta) {
        if (postId == null || delta == 0L) {
            return;
        }
        redisTemplate.execute(
                UPDATE_COUNTER_SCRIPT,
                List.of(counterKey(postId), DIRTY_KEY),
                field,
                Long.toString(delta),
                Double.toString(nowScore()),
                postId.toString()
        );
    }

    private static long longValue(Object raw) {
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static double doubleValue(Object raw) {
        if (raw == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(raw.toString());
        } catch (NumberFormatException ex) {
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

    private static double nowScore() {
        return (double) Instant.now().toEpochMilli();
    }

    private static String counterKey(UUID postId) {
        return COUNTER_KEY_PREFIX + postId;
    }

    private static String viewerKey(UUID postId, String viewerKey) {
        return VIEWER_KEY_PREFIX + postId + ":" + viewerKey.trim();
    }
}
