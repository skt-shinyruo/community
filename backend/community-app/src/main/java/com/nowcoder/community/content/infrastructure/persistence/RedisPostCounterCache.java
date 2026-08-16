package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.PostCounterCache;
import com.nowcoder.community.content.domain.model.PostCounterSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public class RedisPostCounterCache implements PostCounterCache {

    private static final int DIRTY_SHARD_COUNT = 32;
    private static final String COUNTER_KEY_PREFIX = "post:counter:v2:";
    private static final String VIEWER_KEY_PREFIX = "post:viewer:v2:";

    private static final String FIELD_INITIALIZED = "initialized";
    private static final String FIELD_BASE_VIEW = "baseViewCount";
    private static final String FIELD_BASE_LIKE = "baseLikeCount";
    private static final String FIELD_BASE_COMMENT = "baseCommentCount";
    private static final String FIELD_BASE_BOOKMARK = "baseBookmarkCount";
    private static final String FIELD_BASE_SCORE = "baseScore";
    private static final String FIELD_BASE_REVISION = "baseRevision";
    private static final String FIELD_DELTA_VIEW = "deltaViewCount";
    private static final String FIELD_RECOVERY_VIEW_DELTA = "recoveryViewDelta";
    private static final String FIELD_DELTA_LIKE = "deltaLikeCount";
    private static final String FIELD_DELTA_COMMENT = "deltaCommentCount";
    private static final String FIELD_DELTA_BOOKMARK = "deltaBookmarkCount";
    private static final String FIELD_BOOKMARK_ABSOLUTE = "bookmarkCountAbsolute";
    private static final String FIELD_SCORE_OVERLAY = "scoreOverlay";

    private static final DefaultRedisScript<Long> QUARANTINE_DAMAGED_BASELINE_SCRIPT = new DefaultRedisScript<>(
            """
            local delta = redis.call('HGET', KEYS[1], 'deltaViewCount')
            if delta ~= false then
              if ARGV[1] == '1' then
                local recovered = redis.call('HGET', KEYS[1], 'recoveryViewDelta')
                local recoveryCheck = recovered == false
                  or redis.pcall('HINCRBY', KEYS[1], 'recoveryViewDelta', 0)
                if recovered == false
                  or (type(recoveryCheck) == 'table' and recoveryCheck.err ~= nil) then
                  redis.call('HSET', KEYS[1], 'recoveryViewDelta', delta)
                else
                  redis.call('HINCRBY', KEYS[1], 'recoveryViewDelta', delta)
                end
              end
              redis.call('HDEL', KEYS[1], 'deltaViewCount')
            end
            redis.call('HDEL', KEYS[1], 'initialized')
            for index = 2, #ARGV do
              redis.call('HDEL', KEYS[1], ARGV[index])
            end
            return 1
            """,
            Long.class
    );

    private static final DefaultRedisScript<Long> INITIALIZE_SCRIPT = new DefaultRedisScript<>(
            """
            local created = redis.call('HSETNX', KEYS[1], 'initialized', '1')
            if created == 1 then
              local recoveredViewDelta = redis.call('HGET', KEYS[1], 'recoveryViewDelta')
              redis.call('HSET', KEYS[1],
                'baseViewCount', ARGV[1],
                'baseLikeCount', ARGV[2],
                'baseCommentCount', ARGV[3],
                'baseBookmarkCount', ARGV[4],
                'baseScore', ARGV[5],
                'baseRevision', ARGV[6])
              redis.call('HDEL', KEYS[1],
                'deltaViewCount', 'deltaLikeCount', 'deltaCommentCount',
                'deltaBookmarkCount', 'bookmarkCountAbsolute', 'scoreOverlay',
                'recoveryViewDelta')
              if recoveredViewDelta ~= false then
                redis.call('HSET', KEYS[1], 'deltaViewCount', recoveredViewDelta)
              end
            end
            local floor = tonumber(ARGV[6]) or 0
            local sequence = tonumber(redis.call('GET', KEYS[3]) or '0')
            if sequence < floor then
              redis.call('SET', KEYS[3], floor)
            end
            local dirty = redis.call('ZSCORE', KEYS[2], ARGV[7])
            if dirty ~= false and tonumber(dirty) <= floor then
              local revision = redis.call('INCR', KEYS[3])
              redis.call('ZADD', KEYS[2], revision, ARGV[7])
            end
            return created
            """,
            Long.class
    );

    private static final DefaultRedisScript<Long> RECORD_VIEW_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('HSETNX', KEYS[1], 'initialized', '1') == 1 then
              local recoveredViewDelta = redis.call('HGET', KEYS[1], 'recoveryViewDelta')
              redis.call('HSET', KEYS[1],
                'baseViewCount', ARGV[1],
                'baseLikeCount', ARGV[2],
                'baseCommentCount', ARGV[3],
                'baseBookmarkCount', ARGV[4],
                'baseScore', ARGV[5],
                'baseRevision', ARGV[6])
              redis.call('HDEL', KEYS[1],
                'deltaViewCount', 'deltaLikeCount', 'deltaCommentCount',
                'deltaBookmarkCount', 'bookmarkCountAbsolute', 'scoreOverlay',
                'recoveryViewDelta')
              if recoveredViewDelta ~= false then
                redis.call('HSET', KEYS[1], 'deltaViewCount', recoveredViewDelta)
              end
            end
            local floor = tonumber(ARGV[6]) or 0
            local sequence = tonumber(redis.call('GET', KEYS[4]) or '0')
            if sequence < floor then
              redis.call('SET', KEYS[4], floor)
            end
            if not redis.call('SET', KEYS[2], ARGV[7], 'PX', ARGV[8], 'NX') then
              return 0
            end
            redis.call('HINCRBY', KEYS[1], 'deltaViewCount', 1)
            local revision = redis.call('INCR', KEYS[4])
            redis.call('ZADD', KEYS[3], revision, ARGV[9])
            return 1
            """,
            Long.class
    );

    private static final DefaultRedisScript<Long> MARK_DIRTY_SCRIPT = new DefaultRedisScript<>(
            """
            local revision = redis.call('INCR', KEYS[2])
            redis.call('ZADD', KEYS[1], revision, ARGV[1])
            return revision
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
    private final Clock clock;
    private final AtomicInteger dirtyScanCursor = new AtomicInteger();

    public RedisPostCounterCache(
            StringRedisTemplate redisTemplate,
            @Value("${content.counter.viewer-window-seconds:86400}") long viewerWindowSeconds,
            Clock clock
    ) {
        this.redisTemplate = java.util.Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.viewerWindow = Duration.ofSeconds(Math.max(60L, viewerWindowSeconds));
        this.clock = java.util.Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public PostCounterSnapshot get(UUID postId) {
        if (postId == null) {
            return PostCounterSnapshot.empty();
        }
        String counterKey = counterKey(postId);
        Map<Object, Object> values = entries(counterKey);
        List<Object> invalidFields = new ArrayList<>();
        boolean initialized = "1".equals(stringValue(values.get(FIELD_INITIALIZED)));

        long baseViewCount = longValue(values.get(FIELD_BASE_VIEW), FIELD_BASE_VIEW, invalidFields);
        long deltaViewCount = longValue(values.get(FIELD_DELTA_VIEW), FIELD_DELTA_VIEW, invalidFields);
        long likeCount = longValue(values.get(FIELD_BASE_LIKE), FIELD_BASE_LIKE, invalidFields);
        long commentCount = longValue(values.get(FIELD_BASE_COMMENT), FIELD_BASE_COMMENT, invalidFields);
        long bookmarkCount = longValue(values.get(FIELD_BASE_BOOKMARK), FIELD_BASE_BOOKMARK, invalidFields);
        double score = doubleValue(values.get(FIELD_BASE_SCORE), FIELD_BASE_SCORE, invalidFields);
        long revision = longValue(values.get(FIELD_BASE_REVISION), FIELD_BASE_REVISION, invalidFields);

        boolean damagedBaseline = hasDamagedBaseline(values, initialized, invalidFields);
        long viewCount = damagedBaseline
                ? baseViewCount
                : addCounts(baseViewCount, deltaViewCount);
        if (!initialized && !damagedBaseline) {
            likeCount = addCounts(likeCount,
                    longValue(values.get(FIELD_DELTA_LIKE), FIELD_DELTA_LIKE, invalidFields));
            commentCount = addCounts(commentCount,
                    longValue(values.get(FIELD_DELTA_COMMENT), FIELD_DELTA_COMMENT, invalidFields));
            bookmarkCount = values.containsKey(FIELD_BOOKMARK_ABSOLUTE)
                    ? longValue(values.get(FIELD_BOOKMARK_ABSOLUTE), FIELD_BOOKMARK_ABSOLUTE, invalidFields)
                    : addCounts(bookmarkCount,
                            longValue(values.get(FIELD_DELTA_BOOKMARK), FIELD_DELTA_BOOKMARK, invalidFields));
            score = values.containsKey(FIELD_SCORE_OVERLAY)
                    ? doubleValue(values.get(FIELD_SCORE_OVERLAY), FIELD_SCORE_OVERLAY, invalidFields)
                    : score;
        }

        if (damagedBaseline) {
            quarantineDamagedBaseline(counterKey, invalidFields);
        } else if (!invalidFields.isEmpty()) {
            deleteInvalidFields(counterKey, invalidFields);
        }
        return new PostCounterSnapshot(postId, viewCount, likeCount, commentCount, bookmarkCount, score, revision);
    }

    @Override
    public boolean isInitialized(UUID postId) {
        if (postId == null) {
            return false;
        }
        Object initialized = redisTemplate.opsForHash().get(counterKey(postId), FIELD_INITIALIZED);
        return "1".equals(stringValue(initialized));
    }

    @Override
    public void initializeIfAbsent(PostCounterSnapshot baseline) {
        if (baseline == null || baseline.postId() == null) {
            return;
        }
        redisTemplate.execute(
                INITIALIZE_SCRIPT,
                List.of(counterKey(baseline.postId()), dirtyKey(baseline.postId()), dirtySequenceKey(baseline.postId())),
                Long.toString(baseline.viewCount()),
                Long.toString(baseline.likeCount()),
                Long.toString(baseline.commentCount()),
                Long.toString(baseline.bookmarkCount()),
                Double.toString(baseline.score()),
                Long.toString(baseline.revision()),
                baseline.postId().toString()
        );
    }

    @Override
    public boolean recordView(
            UUID postId,
            String viewerKey,
            Instant viewedAt,
            PostCounterSnapshot initializationBaseline
    ) {
        if (postId == null || !StringUtils.hasText(viewerKey)) {
            return false;
        }
        PostCounterSnapshot baseline = normalizeBaseline(postId, initializationBaseline);
        Instant instant = viewedAt == null ? clock.instant() : viewedAt;
        Long result = redisTemplate.execute(
                RECORD_VIEW_SCRIPT,
                List.of(counterKey(postId), viewerKey(postId, viewerKey), dirtyKey(postId), dirtySequenceKey(postId)),
                Long.toString(baseline.viewCount()),
                Long.toString(baseline.likeCount()),
                Long.toString(baseline.commentCount()),
                Long.toString(baseline.bookmarkCount()),
                Double.toString(baseline.score()),
                Long.toString(baseline.revision()),
                Long.toString(instant.toEpochMilli()),
                Long.toString(viewerWindow.toMillis()),
                postId.toString()
        );
        return result != null && result > 0;
    }

    @Override
    public void markDirty(UUID postId) {
        if (postId == null) {
            return;
        }
        markDirtyInternal(postId);
    }

    @Override
    public List<DirtyPost> dirtyPosts(int limit) {
        int size = Math.max(1, limit);
        List<DirtyPost> result = new ArrayList<>(size);
        Set<UUID> selectedPostIds = new HashSet<>();
        List<String> activeQueues = new ArrayList<>();

        int start = Math.floorMod(dirtyScanCursor.getAndIncrement(), DIRTY_SHARD_COUNT);
        int perShardQuota = Math.max(1, size / DIRTY_SHARD_COUNT);
        for (int offset = 0; offset < DIRTY_SHARD_COUNT && result.size() < size; offset++) {
            int shard = (start + offset) % DIRTY_SHARD_COUNT;
            int quota = Math.min(perShardQuota, size - result.size());
            String key = dirtyKey(shard);
            int added = appendDirty(result, selectedPostIds, key, quota, size);
            if (added == quota) {
                activeQueues.add(key);
            }
        }

        redistributeUnusedBudget(result, selectedPostIds, activeQueues, size);
        return List.copyOf(result);
    }

    @Override
    public void clearDirtyPosts(List<DirtyPost> dirtyPosts) {
        if (dirtyPosts == null || dirtyPosts.isEmpty()) {
            return;
        }
        Map<Integer, List<String>> argsByShard = new LinkedHashMap<>();
        for (DirtyPost dirtyPost : dirtyPosts) {
            if (dirtyPost == null || dirtyPost.postId() == null || dirtyPost.revision() <= 0L) {
                continue;
            }
            int shard = shard(dirtyPost.postId());
            List<String> args = argsByShard.computeIfAbsent(shard, ignored -> new ArrayList<>());
            args.add(dirtyPost.postId().toString());
            args.add(Long.toString(dirtyPost.revision()));
        }
        argsByShard.forEach((shard, args) -> redisTemplate.execute(
                CLEAR_DIRTY_SCRIPT,
                List.of(dirtyKey(shard)),
                args.toArray()
        ));
    }

    private void redistributeUnusedBudget(
            List<DirtyPost> target,
            Set<UUID> selectedPostIds,
            List<String> candidates,
            int totalLimit
    ) {
        List<String> activeQueues = candidates;
        while (target.size() < totalLimit && !activeQueues.isEmpty()) {
            int remaining = totalLimit - target.size();
            int perQueueQuota = Math.max(1, (remaining + activeQueues.size() - 1) / activeQueues.size());
            List<String> nextActiveQueues = new ArrayList<>(activeQueues.size());
            boolean madeProgress = false;
            for (String key : activeQueues) {
                if (target.size() >= totalLimit) {
                    break;
                }
                int quota = Math.min(perQueueQuota, totalLimit - target.size());
                int added = appendDirty(target, selectedPostIds, key, quota, totalLimit);
                madeProgress |= added > 0;
                if (added == quota) {
                    nextActiveQueues.add(key);
                }
            }
            if (!madeProgress) {
                return;
            }
            activeQueues = nextActiveQueues;
        }
    }

    private int appendDirty(
            List<DirtyPost> target,
            Set<UUID> selectedPostIds,
            String key,
            int requestedAdditions,
            int totalLimit
    ) {
        int initialSize = target.size();
        int wanted = Math.min(Math.max(0, requestedAdditions), totalLimit - initialSize);
        int prefixSize = Math.max(1, wanted);
        while (target.size() - initialSize < wanted && target.size() < totalLimit) {
            var tuples = redisTemplate.opsForZSet().rangeWithScores(key, 0, prefixSize - 1L);
            if (tuples == null || tuples.isEmpty()) {
                break;
            }
            List<String> poisonMembers = new ArrayList<>();
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                String member = tuple == null ? null : tuple.getValue();
                UUID postId = parseUuid(member);
                Long revision = tuple == null ? null : parseRevision(tuple.getScore());
                if (postId == null || revision == null || !key.equals(dirtyKey(postId))) {
                    if (member != null) {
                        poisonMembers.add(member);
                    }
                    continue;
                }
                if (selectedPostIds.add(postId)) {
                    target.add(new DirtyPost(postId, revision));
                    if (target.size() - initialSize >= wanted || target.size() >= totalLimit) {
                        break;
                    }
                }
            }
            long removedPoison = removePoisonMembers(key, poisonMembers);
            if (target.size() - initialSize >= wanted || tuples.size() < prefixSize) {
                break;
            }
            if (poisonMembers.isEmpty() || removedPoison == 0L) {
                prefixSize += Math.max(1, wanted - (target.size() - initialSize));
            }
        }
        return target.size() - initialSize;
    }

    private long removePoisonMembers(String key, List<String> poisonMembers) {
        if (!poisonMembers.isEmpty()) {
            Long removed = redisTemplate.opsForZSet().remove(key, poisonMembers.toArray());
            return removed == null ? 0L : removed;
        }
        return 0L;
    }

    private long markDirtyInternal(UUID postId) {
        Long revision = redisTemplate.execute(
                MARK_DIRTY_SCRIPT,
                List.of(dirtyKey(postId), dirtySequenceKey(postId)),
                postId.toString()
        );
        if (revision == null || revision <= 0L) {
            throw new IllegalStateException("post counter dirty revision allocation failed");
        }
        return revision;
    }

    private Map<Object, Object> entries(String key) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(key);
        return values == null ? Map.of() : values;
    }

    private void quarantineDamagedBaseline(String key, List<Object> invalidFields) {
        List<Object> arguments = new ArrayList<>(invalidFields.size() + 1);
        arguments.add(invalidFields.contains(FIELD_DELTA_VIEW) ? "0" : "1");
        arguments.addAll(invalidFields);
        Long quarantined = redisTemplate.execute(
                QUARANTINE_DAMAGED_BASELINE_SCRIPT,
                List.of(key),
                arguments.toArray()
        );
        if (quarantined == null || quarantined != 1L) {
            throw new IllegalStateException("post counter damaged baseline quarantine failed");
        }
    }

    private static boolean hasDamagedBaseline(
            Map<Object, Object> values,
            boolean initialized,
            List<Object> invalidFields
    ) {
        boolean markerPresent = values.containsKey(FIELD_INITIALIZED);
        boolean anyBaselineFieldPresent = values.containsKey(FIELD_BASE_VIEW)
                || values.containsKey(FIELD_BASE_LIKE)
                || values.containsKey(FIELD_BASE_COMMENT)
                || values.containsKey(FIELD_BASE_BOOKMARK)
                || values.containsKey(FIELD_BASE_SCORE)
                || values.containsKey(FIELD_BASE_REVISION);
        boolean completeBaseline = values.containsKey(FIELD_BASE_VIEW)
                && values.containsKey(FIELD_BASE_LIKE)
                && values.containsKey(FIELD_BASE_COMMENT)
                && values.containsKey(FIELD_BASE_BOOKMARK)
                && values.containsKey(FIELD_BASE_SCORE)
                && values.containsKey(FIELD_BASE_REVISION);
        boolean invalidBaselineField = invalidFields.stream()
                .map(Object::toString)
                .anyMatch(RedisPostCounterCache::isBaselineField);
        return values.containsKey(FIELD_RECOVERY_VIEW_DELTA)
                || (markerPresent && !initialized)
                || (!markerPresent && anyBaselineFieldPresent)
                || (initialized && (!completeBaseline || invalidBaselineField));
    }

    private static boolean isBaselineField(String field) {
        return FIELD_BASE_VIEW.equals(field)
                || FIELD_BASE_LIKE.equals(field)
                || FIELD_BASE_COMMENT.equals(field)
                || FIELD_BASE_BOOKMARK.equals(field)
                || FIELD_BASE_SCORE.equals(field)
                || FIELD_BASE_REVISION.equals(field);
    }

    private void deleteInvalidFields(String key, List<Object> invalidFields) {
        if (!invalidFields.isEmpty()) {
            redisTemplate.opsForHash().delete(key, invalidFields.toArray());
        }
    }

    private static PostCounterSnapshot normalizeBaseline(UUID postId, PostCounterSnapshot baseline) {
        if (baseline == null) {
            return new PostCounterSnapshot(postId, 0L, 0L, 0L, 0L, 0.0);
        }
        return new PostCounterSnapshot(
                postId,
                baseline.viewCount(),
                baseline.likeCount(),
                baseline.commentCount(),
                baseline.bookmarkCount(),
                baseline.score(),
                baseline.revision()
        );
    }

    private static long addCounts(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            return right >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private static long longValue(Object raw, String field, List<Object> invalidFields) {
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException ex) {
            invalidFields.add(field);
            return 0L;
        }
    }

    private static double doubleValue(Object raw, String field, List<Object> invalidFields) {
        if (raw == null) {
            return 0.0;
        }
        try {
            double value = Double.parseDouble(raw.toString());
            if (!Double.isFinite(value)) {
                throw new NumberFormatException("non-finite");
            }
            return value;
        } catch (NumberFormatException ex) {
            invalidFields.add(field);
            return 0.0;
        }
    }

    private static String stringValue(Object raw) {
        return raw == null ? null : raw.toString();
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

    private static Long parseRevision(Double score) {
        if (score == null
                || !Double.isFinite(score)
                || score < 1.0
                || score >= 0x1.0p63
                || score != Math.rint(score)) {
            return null;
        }
        return score.longValue();
    }

    private static int shard(UUID postId) {
        return Math.floorMod(postId.hashCode(), DIRTY_SHARD_COUNT);
    }

    private static String counterKey(UUID postId) {
        return COUNTER_KEY_PREFIX + hashTag(shard(postId)) + ":" + postId;
    }

    private static String dirtyKey(UUID postId) {
        return dirtyKey(shard(postId));
    }

    private static String dirtyKey(int shard) {
        return COUNTER_KEY_PREFIX + hashTag(shard) + ":dirty";
    }

    private static String dirtySequenceKey(UUID postId) {
        return COUNTER_KEY_PREFIX + hashTag(shard(postId)) + ":sequence";
    }

    private static String viewerKey(UUID postId, String viewerKey) {
        return VIEWER_KEY_PREFIX + hashTag(shard(postId)) + ":" + postId + ":" + sha256(viewerKey.trim());
    }

    private static String hashTag(int shard) {
        String value = Integer.toHexString(shard);
        return "{post-counter-" + (value.length() == 1 ? "0" + value : value) + "}";
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(Character.forDigit((item >>> 4) & 0xF, 16));
                hex.append(Character.forDigit(item & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
