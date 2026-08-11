package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.FeedCursorCodec;
import com.nowcoder.community.content.application.PostFeedCache;
import com.nowcoder.community.content.application.result.HotFeedDegradationSignalResult;
import com.nowcoder.community.content.domain.model.Category;
import com.nowcoder.community.content.domain.repository.CategoryContentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "content.storage", havingValue = "redis", matchIfMissing = true)
public class RedisPostFeedCache implements PostFeedCache {

    private static final int MAX_PAGE_SIZE = 50;
    private static final String GLOBAL_HOT_KEY = "post:feed:global:hot";
    private static final String GLOBAL_HOT_RANK_VERSION_KEY = GLOBAL_HOT_KEY + ":rank-version";
    private static final String BOARD_HOT_KEY_PREFIX = "post:feed:board:hot:";
    private static final String PROJECTION_KEY_PREFIX = "post:feed:projection:";
    private static final String PROJECTION_MEMBER_KEY_PREFIX = "post:feed:projection-member:";
    private static final String PROJECTION_EPOCH_KEY_PREFIX = "post:feed:projection-epoch:";
    private static final String TERMINAL_MEMBER_KEY_PREFIX = "post:feed:terminal-members:";
    private static final String VERSION_MEMBER_KEY_PREFIX = "post:feed:version-members:";
    private static final String SCORE_VERSION_MEMBER_KEY_PREFIX = "post:feed:score-version-members:";
    private static final String TERMINAL_FENCE_TTL_SECONDS = "604800";
    private static final String HOT_DEGRADATION_DEGRADED_KEY = "post:feed:hot:degradation:degraded";
    private static final String HOT_DEGRADATION_REASON_KEY = "post:feed:hot:degradation:reason";
    private static final String HOT_DEGRADATION_UPDATED_AT_KEY = "post:feed:hot:degradation:updated-at";
    private static final String LAST_PREWARM_KEY_PREFIX = "post:feed:hot:prewarm:last:";
    private static final DefaultRedisScript<Long> UPSERT_PROJECTION_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then
              redis.call('ZREM', KEYS[1], ARGV[1])
              local fencedMember = redis.call('GET', KEYS[6])
              if fencedMember then
                local removed = redis.call('ZREM', KEYS[5], fencedMember)
                redis.call('DEL', KEYS[6])
                if removed > 0 then
                  redis.call('INCR', KEYS[7])
                end
              end
              return 0
            end
            local minimumVersion = tonumber(redis.call('GET', KEYS[3]) or '0')
            local minimumScoreVersion = tonumber(redis.call('GET', KEYS[4]) or '0')
            local aggregateVersion = tonumber(ARGV[3])
            local scoreVersion = tonumber(ARGV[4])
            if aggregateVersion == nil or scoreVersion == nil then
              return 0
            end
            if aggregateVersion < minimumVersion then
              return 0
            end
            if aggregateVersion == minimumVersion and scoreVersion < minimumScoreVersion then
              return 0
            end
            local oldMember = redis.call('GET', KEYS[6])
            local removed = 0
            if oldMember and oldMember ~= ARGV[6] then
              removed = redis.call('ZREM', KEYS[5], oldMember)
            end
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
            local added = redis.call('ZADD', KEYS[5], 'CH', 0, ARGV[6])
            redis.call('SET', KEYS[6], ARGV[6])
            redis.call('SET', KEYS[3], ARGV[3], 'EX', ARGV[5])
            redis.call('SET', KEYS[4], ARGV[4], 'EX', ARGV[5])
            if removed > 0 or added > 0 then
              redis.call('INCR', KEYS[7])
            end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> REMOVE_SCRIPT = new DefaultRedisScript<>("""
            local minimumVersion = tonumber(redis.call('GET', KEYS[2]) or '0')
            local nextVersion = tonumber(ARGV[2])
            if nextVersion > minimumVersion then
              redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])
            elseif redis.call('EXISTS', KEYS[2]) == 1 then
              redis.call('EXPIRE', KEYS[2], ARGV[3])
            end
            redis.call('ZREM', KEYS[1], ARGV[1])
            local oldMember = redis.call('GET', KEYS[4])
            if oldMember then
              local removed = redis.call('ZREM', KEYS[3], oldMember)
              redis.call('DEL', KEYS[4])
              if removed > 0 then
                redis.call('INCR', KEYS[5])
              end
            end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> TERMINAL_REMOVE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[2], '1', 'EX', ARGV[2])
            local minimumVersion = tonumber(redis.call('GET', KEYS[3]) or '0')
            local nextVersion = tonumber(ARGV[3])
            if nextVersion > minimumVersion then
              redis.call('SET', KEYS[3], ARGV[3], 'EX', ARGV[2])
            end
            redis.call('ZREM', KEYS[1], ARGV[1])
            local oldMember = redis.call('GET', KEYS[5])
            if oldMember then
              local removed = redis.call('ZREM', KEYS[4], oldMember)
              redis.call('DEL', KEYS[5])
              if removed > 0 then
                redis.call('INCR', KEYS[6])
              end
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final FeedCursorCodec feedCursorCodec;
    private final CategoryContentRepository categoryContentRepository;
    private final Clock clock;

    public RedisPostFeedCache(
            StringRedisTemplate redisTemplate,
            FeedCursorCodec feedCursorCodec,
            CategoryContentRepository categoryContentRepository,
            Clock clock
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.feedCursorCodec = Objects.requireNonNull(feedCursorCodec, "feedCursorCodec must not be null");
        this.categoryContentRepository = Objects.requireNonNull(
                categoryContentRepository, "categoryContentRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public HotProjectionPage readGlobalHotProjection(String cursor, int size) {
        return readProjection(GLOBAL_HOT_KEY, cursor, size);
    }

    @Override
    public HotProjectionPage readBoardHotProjection(UUID boardId, String cursor, int size) {
        return boardId == null ? null : readProjection(boardKey(boardId), cursor, size);
    }

    @Override
    public void upsertGlobalHot(
            HotProjectionEntry entry,
            String rankVersion,
            long aggregateVersion,
            long scoreVersion
    ) {
        upsertProjection(GLOBAL_HOT_KEY, entry, aggregateVersion, scoreVersion);
    }

    @Override
    public void upsertBoardHot(
            UUID boardId,
            HotProjectionEntry entry,
            String rankVersion,
            long aggregateVersion,
            long scoreVersion
    ) {
        if (boardId != null) {
            upsertProjection(boardKey(boardId), entry, aggregateVersion, scoreVersion);
        }
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
        Instant now = clock.instant();
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
        remove(postId, boardId, 0L);
    }

    @Override
    public void remove(UUID postId, UUID boardId, long minimumVersion) {
        if (postId == null) {
            return;
        }
        removeFromScope(GLOBAL_HOT_KEY, postId, minimumVersion);
        if (boardId != null) {
            removeFromScope(boardKey(boardId), postId, minimumVersion);
            return;
        }
        List<Category> categories = categoryContentRepository.listCategories();
        if (categories == null) {
            return;
        }
        for (Category category : categories) {
            if (category != null && category.getId() != null) {
                removeFromScope(boardKey(category.getId()), postId, minimumVersion);
            }
        }
    }

    @Override
    public void terminalRemove(UUID postId, UUID boardId) {
        terminalRemove(postId, boardId, 0L);
    }

    @Override
    public void terminalRemove(UUID postId, UUID boardId, long minimumVersion) {
        if (postId == null) {
            return;
        }
        terminalRemoveFromScope(GLOBAL_HOT_KEY, postId, minimumVersion);
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
            terminalRemoveFromScope(boardKey(currentBoardId), postId, minimumVersion);
        }
    }

    private void upsertProjection(
            String feedKey,
            HotProjectionEntry entry,
            long aggregateVersion,
            long scoreVersion
    ) {
        if (!validProjectionEntry(entry)) {
            return;
        }
        UUID postId = entry.postId();
        String member = projectionMember(entry);
        redisTemplate.execute(
                UPSERT_PROJECTION_SCRIPT,
                List.of(
                        feedKey,
                        terminalMemberKey(feedKey, postId),
                        versionMemberKey(feedKey, postId),
                        scoreVersionMemberKey(feedKey, postId),
                        projectionKey(feedKey),
                        projectionMemberKey(feedKey, postId),
                        projectionEpochKey(feedKey)
                ),
                postId.toString(),
                Double.toString(entry.score()),
                Long.toString(Math.max(0L, aggregateVersion)),
                Long.toString(Math.max(0L, scoreVersion)),
                TERMINAL_FENCE_TTL_SECONDS,
                member
        );
    }

    private void removeFromScope(String feedKey, UUID postId, long minimumVersion) {
        redisTemplate.execute(
                REMOVE_SCRIPT,
                List.of(
                        feedKey,
                        versionMemberKey(feedKey, postId),
                        projectionKey(feedKey),
                        projectionMemberKey(feedKey, postId),
                        projectionEpochKey(feedKey)
                ),
                postId.toString(),
                Long.toString(Math.max(0L, minimumVersion)),
                TERMINAL_FENCE_TTL_SECONDS
        );
    }

    private void terminalRemoveFromScope(String feedKey, UUID postId, long minimumVersion) {
        Long removed = redisTemplate.execute(
                TERMINAL_REMOVE_SCRIPT,
                List.of(
                        feedKey,
                        terminalMemberKey(feedKey, postId),
                        versionMemberKey(feedKey, postId),
                        projectionKey(feedKey),
                        projectionMemberKey(feedKey, postId),
                        projectionEpochKey(feedKey)
                ),
                postId.toString(),
                TERMINAL_FENCE_TTL_SECONDS,
                Long.toString(Math.max(0L, minimumVersion))
        );
        if (!Long.valueOf(1L).equals(removed)) {
            throw new IllegalStateException(
                    "post feed terminal fence was not persisted: postId=" + postId + ", feedKey=" + feedKey
            );
        }
    }

    private HotProjectionPage readProjection(String feedKey, String cursor, int size) {
        int pageSize = limit(cursor, size);
        FeedCursorCodec.CursorState state = feedCursorCodec.decode(cursor);
        if (state.page() > 0 && !state.hasHotBoundary()) {
            return new HotProjectionPage(List.of(), 0L, false);
        }
        Range<String> range = state.hasHotBoundary()
                ? Range.leftUnbounded(Range.Bound.exclusive(projectionMember(state.hotBoundary())))
                : Range.unbounded();
        String projectionKey = projectionKey(feedKey);
        String epochKey = projectionEpochKey(feedKey);
        for (int attempt = 0; attempt < 2; attempt++) {
            long beforeEpoch = readProjectionEpoch(epochKey);
            if (beforeEpoch <= 0L || (state.projectionEpoch() > 0L && state.projectionEpoch() != beforeEpoch)) {
                return new HotProjectionPage(List.of(), 0L, false);
            }
            Set<String> rawMembers = redisTemplate.opsForZSet().reverseRangeByLex(
                    projectionKey,
                    range,
                    Limit.limit().count(pageSize + 1)
            );
            long afterEpoch = readProjectionEpoch(epochKey);
            if (beforeEpoch != afterEpoch) {
                continue;
            }
            if (rawMembers == null || rawMembers.isEmpty()) {
                return new HotProjectionPage(List.of(), beforeEpoch, false);
            }
            List<HotProjectionEntry> entries = new ArrayList<>(Math.min(pageSize, rawMembers.size()));
            List<String> poisonMembers = new ArrayList<>();
            for (String rawMember : rawMembers) {
                HotProjectionEntry entry = parseProjectionMember(rawMember);
                if (entry == null) {
                    if (rawMember != null) {
                        poisonMembers.add(rawMember);
                    }
                    continue;
                }
                entries.add(entry);
            }
            if (!poisonMembers.isEmpty()) {
                redisTemplate.opsForZSet().remove(projectionKey, poisonMembers.toArray(Object[]::new));
                redisTemplate.opsForValue().increment(epochKey);
                continue;
            }
            boolean hasNext = entries.size() > pageSize;
            List<HotProjectionEntry> pageEntries = hasNext
                    ? List.copyOf(entries.subList(0, pageSize))
                    : List.copyOf(entries);
            return new HotProjectionPage(pageEntries, beforeEpoch, hasNext);
        }
        return new HotProjectionPage(List.of(), 0L, false);
    }

    private long readProjectionEpoch(String epochKey) {
        String value = redisTemplate.opsForValue().get(epochKey);
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static boolean validProjectionEntry(HotProjectionEntry entry) {
        return entry != null
                && entry.postId() != null
                && Double.isFinite(entry.score())
                && entry.createTime() != null;
    }

    static String projectionMember(HotProjectionEntry entry) {
        if (!validProjectionEntry(entry)) {
            throw new IllegalArgumentException("complete hot feed projection entry is required");
        }
        return sortableInt(entry.type())
                + sortableDouble(entry.score())
                + sortableLong(entry.createTime().getTime())
                + uuidHex(entry.postId());
    }

    private static String projectionMember(FeedCursorCodec.HotBoundary boundary) {
        return projectionMember(new HotProjectionEntry(
                boundary.postId(),
                boundary.type(),
                boundary.score(),
                boundary.createTime()
        ));
    }

    static HotProjectionEntry parseProjectionMember(String member) {
        if (member == null || member.length() != 72) {
            return null;
        }
        try {
            int type = Integer.parseUnsignedInt(member.substring(0, 8), 16) ^ Integer.MIN_VALUE;
            long sortableScore = Long.parseUnsignedLong(member.substring(8, 24), 16);
            long scoreBits = (sortableScore & Long.MIN_VALUE) != 0L
                    ? sortableScore ^ Long.MIN_VALUE
                    : ~sortableScore;
            double score = Double.longBitsToDouble(scoreBits);
            long createTime = Long.parseUnsignedLong(member.substring(24, 40), 16) ^ Long.MIN_VALUE;
            long mostSignificantBits = Long.parseUnsignedLong(member.substring(40, 56), 16);
            long leastSignificantBits = Long.parseUnsignedLong(member.substring(56, 72), 16);
            if (!Double.isFinite(score)) {
                return null;
            }
            return new HotProjectionEntry(
                    new UUID(mostSignificantBits, leastSignificantBits),
                    type,
                    score,
                    new Date(createTime)
            );
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String sortableInt(int value) {
        return String.format(Locale.ROOT, "%08x", value ^ Integer.MIN_VALUE);
    }

    private static String sortableDouble(double value) {
        double normalized = value == 0.0d ? 0.0d : value;
        long bits = Double.doubleToRawLongBits(normalized);
        long sortable = bits < 0L ? ~bits : bits ^ Long.MIN_VALUE;
        return String.format(Locale.ROOT, "%016x", sortable);
    }

    private static String sortableLong(long value) {
        return String.format(Locale.ROOT, "%016x", value ^ Long.MIN_VALUE);
    }

    private static String uuidHex(UUID value) {
        return String.format(
                Locale.ROOT,
                "%016x%016x",
                value.getMostSignificantBits(),
                value.getLeastSignificantBits()
        );
    }

    private int limit(String cursor, int size) {
        FeedCursorCodec.CursorState state = feedCursorCodec.decode(cursor);
        int preferred = state.size() > 0 ? state.size() : size;
        return Math.min(MAX_PAGE_SIZE, Math.max(1, preferred));
    }

    private String boardKey(UUID boardId) {
        return BOARD_HOT_KEY_PREFIX + boardId;
    }

    private String projectionKey(String feedKey) {
        return PROJECTION_KEY_PREFIX + "{" + feedKey + "}";
    }

    private String projectionMemberKey(String feedKey, UUID postId) {
        return PROJECTION_MEMBER_KEY_PREFIX + "{" + feedKey + "}:" + postId;
    }

    private String projectionEpochKey(String feedKey) {
        return PROJECTION_EPOCH_KEY_PREFIX + "{" + feedKey + "}";
    }

    private String terminalMemberKey(String feedKey, UUID postId) {
        return TERMINAL_MEMBER_KEY_PREFIX + "{" + feedKey + "}:" + postId;
    }

    private String versionMemberKey(String feedKey, UUID postId) {
        return VERSION_MEMBER_KEY_PREFIX + "{" + feedKey + "}:" + postId;
    }

    private String scoreVersionMemberKey(String feedKey, UUID postId) {
        return SCORE_VERSION_MEMBER_KEY_PREFIX + "{" + feedKey + "}:" + postId;
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
