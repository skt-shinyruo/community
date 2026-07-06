package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.FeedCursorCodec;
import com.nowcoder.community.content.application.PostFeedCache;
import com.nowcoder.community.content.domain.model.Category;
import com.nowcoder.community.content.domain.repository.CategoryContentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "content.storage", havingValue = "redis", matchIfMissing = true)
public class RedisPostFeedCache implements PostFeedCache {

    private static final String GLOBAL_HOT_KEY = "post:feed:global:hot";
    private static final String GLOBAL_HOT_RANK_VERSION_KEY = GLOBAL_HOT_KEY + ":rank-version";
    private static final String BOARD_HOT_KEY_PREFIX = "post:feed:board:hot:";

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
        redisTemplate.opsForZSet().add(GLOBAL_HOT_KEY, postId.toString(), score);
    }

    @Override
    public void upsertBoardHot(UUID boardId, UUID postId, double score, String rankVersion) {
        if (boardId == null || postId == null) {
            return;
        }
        redisTemplate.opsForZSet().add(boardKey(boardId), postId.toString(), score);
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
        return StringUtils.hasText(rankVersion) ? rankVersion : "hot-v2";
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

    private List<UUID> readIds(String key, String cursor, int size) {
        int limit = limit(cursor, size);
        FeedCursorCodec.CursorState state = feedCursorCodec.decode(cursor);
        long start = (long) Math.max(0, state.page()) * limit;
        long end = start + limit - 1L;
        Set<String> rawIds = redisTemplate.opsForZSet().reverseRange(key, start, end);
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        return rawIds.stream()
                .map(this::parseUuid)
                .filter(id -> id != null)
                .toList();
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
}
