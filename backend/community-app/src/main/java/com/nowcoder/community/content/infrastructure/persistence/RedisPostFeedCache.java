package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.FeedCursorCodec;
import com.nowcoder.community.content.application.PostFeedCache;
import com.nowcoder.community.content.application.result.HotFeedDegradationSignalResult;
import com.nowcoder.community.content.domain.model.Category;
import com.nowcoder.community.content.domain.repository.CategoryContentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "content.storage", havingValue = "redis", matchIfMissing = true)
public class RedisPostFeedCache implements PostFeedCache {

    private static final String GLOBAL_HOT_KEY = "post:feed:global:hot";
    private static final String GLOBAL_HOT_RANK_VERSION_KEY = GLOBAL_HOT_KEY + ":rank-version";
    private static final String BOARD_HOT_KEY_PREFIX = "post:feed:board:hot:";
    private static final String TERMINAL_MEMBER_KEY_PREFIX = "post:feed:terminal-members:";
    private static final String HOT_DEGRADATION_DEGRADED_KEY = "post:feed:hot:degradation:degraded";
    private static final String HOT_DEGRADATION_REASON_KEY = "post:feed:hot:degradation:reason";
    private static final String HOT_DEGRADATION_UPDATED_AT_KEY = "post:feed:hot:degradation:updated-at";
    private static final String LAST_PREWARM_KEY_PREFIX = "post:feed:hot:prewarm:last:";
    private static final DefaultRedisScript<Long> UPSERT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
              redis.call('ZREM', KEYS[1], ARGV[1])
              return 0
            end
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> TERMINAL_REMOVE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('SADD', KEYS[2], ARGV[1])
            redis.call('ZREM', KEYS[1], ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final FeedCursorCodec feedCursorCodec;
    private final CategoryContentRepository categoryContentRepository;

    public RedisPostFeedCache(
            StringRedisTemplate redisTemplate,
            FeedCursorCodec feedCursorCodec,
            CategoryContentRepository categoryContentRepository
    ) {
        this.redisTemplate = redisTemplate;
        this.feedCursorCodec = feedCursorCodec;
        this.categoryContentRepository = categoryContentRepository;
    }

    @Override
    public List<UUID> readGlobalHotIds(String cursor, int size) {
        return readIds(GLOBAL_HOT_KEY, cursor, size);
    }

    @Override
    public List<UUID> readBoardHotIds(UUID boardId, String cursor, int size) {
        if (boardId == null) {
            return List.of();
        }
        return readIds(boardKey(boardId), cursor, size);
    }

    @Override
    public void upsertGlobalHot(UUID postId, double score, String rankVersion) {
        if (postId == null) {
            return;
        }
        upsert(GLOBAL_HOT_KEY, postId, score);
    }

    @Override
    public void upsertBoardHot(UUID boardId, UUID postId, double score, String rankVersion) {
        if (boardId == null || postId == null) {
            return;
        }
        upsert(boardKey(boardId), postId, score);
    }

    @Override
    public void writeRankVersion(String rankVersion) {
        if (!StringUtils.hasText(rankVersion)) {
            return;
        }
        redisTemplate.opsForValue().set(GLOBAL_HOT_RANK_VERSION_KEY, rankVersion);
    }

    @Override
    public String readRankVersion() {
        String rankVersion = redisTemplate.opsForValue().get(GLOBAL_HOT_RANK_VERSION_KEY);
        if (rankVersion == null) {
            return "hot-v2";
        }
        if (!StringUtils.hasText(rankVersion)) {
            redisTemplate.delete(GLOBAL_HOT_RANK_VERSION_KEY);
            return "hot-v2";
        }
        return rankVersion;
    }

    @Override
    public long countGlobalHot() {
        Long size = redisTemplate.opsForZSet().zCard(GLOBAL_HOT_KEY);
        return size == null ? 0L : size;
    }

    @Override
    public long countBoardHot(UUID boardId) {
        if (boardId == null) {
            return 0L;
        }
        Long size = redisTemplate.opsForZSet().zCard(boardKey(boardId));
        return size == null ? 0L : size;
    }

    @Override
    public HotFeedDegradationSignalResult readDegradationSignal() {
        String degradedValue = redisTemplate.opsForValue().get(HOT_DEGRADATION_DEGRADED_KEY);
        String reason = redisTemplate.opsForValue().get(HOT_DEGRADATION_REASON_KEY);
        String updatedAt = redisTemplate.opsForValue().get(HOT_DEGRADATION_UPDATED_AT_KEY);
        return new HotFeedDegradationSignalResult(
                Boolean.parseBoolean(degradedValue),
                StringUtils.hasText(reason) ? reason : "",
                parseInstant(updatedAt)
        );
    }

    @Override
    public HotFeedDegradationSignalResult writeDegradationSignal(boolean degraded, String reason) {
        Instant now = Instant.now();
        String normalizedReason = StringUtils.hasText(reason) ? reason.trim() : "";
        redisTemplate.opsForValue().set(HOT_DEGRADATION_DEGRADED_KEY, Boolean.toString(degraded));
        redisTemplate.opsForValue().set(HOT_DEGRADATION_REASON_KEY, degraded ? normalizedReason : "");
        redisTemplate.opsForValue().set(HOT_DEGRADATION_UPDATED_AT_KEY, now.toString());
        return new HotFeedDegradationSignalResult(degraded, degraded ? normalizedReason : "", now);
    }

    @Override
    public Instant readLastPrewarmAt(String scope, UUID boardId) {
        return parseInstant(redisTemplate.opsForValue().get(lastPrewarmKey(scope, boardId)));
    }

    @Override
    public void writeLastPrewarmAt(String scope, UUID boardId, Instant prewarmAt) {
        if (prewarmAt == null) {
            return;
        }
        redisTemplate.opsForValue().set(lastPrewarmKey(scope, boardId), prewarmAt.toString());
    }

    @Override
    public void remove(UUID postId, UUID boardId) {
        if (postId == null) {
            return;
        }
        redisTemplate.opsForZSet().remove(GLOBAL_HOT_KEY, postId.toString());
        if (boardId != null) {
            redisTemplate.opsForZSet().remove(boardKey(boardId), postId.toString());
            return;
        }
        for (Category category : categoryContentRepository.listCategories()) {
            if (category == null || category.getId() == null) {
                continue;
            }
            redisTemplate.opsForZSet().remove(boardKey(category.getId()), postId.toString());
        }
    }

    @Override
    public void terminalRemove(UUID postId, UUID boardId) {
        if (postId == null) {
            return;
        }
        terminalRemoveFromScope(GLOBAL_HOT_KEY, postId);
        Set<UUID> boardIds = new LinkedHashSet<>();
        if (boardId != null) {
            boardIds.add(boardId);
        }
        List<Category> categories = categoryContentRepository.listCategories();
        if (categories != null) {
            for (Category category : categories) {
                if (category != null && category.getId() != null) {
                    boardIds.add(category.getId());
                }
            }
        }
        for (UUID currentBoardId : boardIds) {
            terminalRemoveFromScope(boardKey(currentBoardId), postId);
        }
    }

    private void upsert(String feedKey, UUID postId, double score) {
        redisTemplate.execute(
                UPSERT_SCRIPT,
                List.of(feedKey, terminalMemberKey(feedKey)),
                postId.toString(),
                Double.toString(score)
        );
    }

    private void terminalRemoveFromScope(String feedKey, UUID postId) {
        Long removed = redisTemplate.execute(
                TERMINAL_REMOVE_SCRIPT,
                List.of(feedKey, terminalMemberKey(feedKey)),
                postId.toString()
        );
        if (!Long.valueOf(1L).equals(removed)) {
            throw new IllegalStateException(
                    "post feed terminal fence was not persisted: postId=" + postId + ", feedKey=" + feedKey
            );
        }
    }

    private List<UUID> readIds(String key, String cursor, int size) {
        int limit = limit(cursor, size);
        FeedCursorCodec.CursorState state = feedCursorCodec.decode(cursor);
        long start = (long) Math.max(0, state.page()) * limit;
        long end = start + limit - 1L;
        Set<String> rawIds = redisTemplate.opsForZSet().reverseRange(key, start, end);
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        List<String> poisonMembers = new ArrayList<>();
        for (String rawId : rawIds) {
            UUID parsed = parseUuid(rawId);
            if (parsed == null) {
                if (StringUtils.hasText(rawId)) {
                    poisonMembers.add(rawId);
                }
                continue;
            }
            ids.add(parsed);
        }
        if (!poisonMembers.isEmpty()) {
            redisTemplate.opsForZSet().remove(key, poisonMembers.toArray(Object[]::new));
        }
        return List.copyOf(ids);
    }

    private int limit(String cursor, int size) {
        FeedCursorCodec.CursorState state = feedCursorCodec.decode(cursor);
        int preferred = state.size() > 0 ? state.size() : size;
        return Math.max(1, preferred);
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String boardKey(UUID boardId) {
        return BOARD_HOT_KEY_PREFIX + boardId;
    }

    private String terminalMemberKey(String feedKey) {
        return TERMINAL_MEMBER_KEY_PREFIX + "{" + feedKey + "}";
    }

    private String lastPrewarmKey(String scope, UUID boardId) {
        if ("board".equals(scope) && boardId != null) {
            return LAST_PREWARM_KEY_PREFIX + "board:" + boardId;
        }
        return LAST_PREWARM_KEY_PREFIX + "global";
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
