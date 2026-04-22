package com.nowcoder.community.social.follow;

import com.nowcoder.community.social.follow.dto.FollowItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "social.storage", havingValue = "redis")
public class RedisFollowRepository implements FollowRepository {

    private static final DefaultRedisScript<Long> FOLLOW_SCRIPT = new DefaultRedisScript<>(
            """
            local followeeKey = KEYS[1]
            local followerKey = KEYS[2]

            local entityId = ARGV[1]
            local userId = ARGV[2]
            local score = tonumber(ARGV[3])

            local followeeScore = redis.call('ZSCORE', followeeKey, entityId)
            local followerScore = redis.call('ZSCORE', followerKey, userId)

            if followeeScore and followerScore then
              return 0
            end

            -- 修复历史/异常窗口导致的“双写不一致”：只补齐缺失的一侧，不重复返回 created=true。
            if followeeScore and not followerScore then
              redis.call('ZADD', followerKey, followeeScore, userId)
              return 0
            end
            if not followeeScore and followerScore then
              redis.call('ZADD', followeeKey, followerScore, entityId)
              return 0
            end

            redis.call('ZADD', followeeKey, score, entityId)
            redis.call('ZADD', followerKey, score, userId)
            return 1
            """,
            Long.class
    );

    private static final DefaultRedisScript<Long> UNFOLLOW_SCRIPT = new DefaultRedisScript<>(
            """
            local followeeKey = KEYS[1]
            local followerKey = KEYS[2]

            local entityId = ARGV[1]
            local userId = ARGV[2]

            local r1 = redis.call('ZREM', followeeKey, entityId)
            local r2 = redis.call('ZREM', followerKey, userId)

            if (r1 and r1 > 0) or (r2 and r2 > 0) then
              return 1
            end
            return 0
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisFollowRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean follow(UUID userId, int entityType, UUID entityId, long followTimeMillis) {
        String followeeKey = followeeKey(userId, entityType);
        String followerKey = followerKey(entityType, entityId);
        Long changed = redisTemplate.execute(
                FOLLOW_SCRIPT,
                List.of(followeeKey, followerKey),
                String.valueOf(entityId),
                String.valueOf(userId),
                String.valueOf(followTimeMillis)
        );
        return changed != null && changed > 0;
    }

    @Override
    public boolean unfollow(UUID userId, int entityType, UUID entityId) {
        String followeeKey = followeeKey(userId, entityType);
        String followerKey = followerKey(entityType, entityId);
        Long changed = redisTemplate.execute(
                UNFOLLOW_SCRIPT,
                List.of(followeeKey, followerKey),
                String.valueOf(entityId),
                String.valueOf(userId)
        );
        return changed != null && changed > 0;
    }

    @Override
    public boolean hasFollowed(UUID userId, int entityType, UUID entityId) {
        Double score = redisTemplate.opsForZSet().score(followeeKey(userId, entityType), String.valueOf(entityId));
        return score != null;
    }

    @Override
    public long countFollowees(UUID userId, int entityType) {
        Long size = redisTemplate.opsForZSet().zCard(followeeKey(userId, entityType));
        return size == null ? 0 : size;
    }

    @Override
    public long countFollowers(int entityType, UUID entityId) {
        Long size = redisTemplate.opsForZSet().zCard(followerKey(entityType, entityId));
        return size == null ? 0 : size;
    }

    @Override
    public List<FollowItem> listFollowees(UUID userId, int entityType, int offset, int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(
                followeeKey(userId, entityType),
                offset,
                (long) offset + (long) limit - 1L
        );
        return toItems(tuples);
    }

    @Override
    public List<FollowItem> listFollowers(int entityType, UUID entityId, int offset, int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(
                followerKey(entityType, entityId),
                offset,
                (long) offset + (long) limit - 1L
        );
        return toItems(tuples);
    }

    @Override
    public boolean requiresExplicitCompensation() {
        return true;
    }

    private List<FollowItem> toItems(Set<ZSetOperations.TypedTuple<String>> tuples) {
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<FollowItem> items = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            if (t == null || t.getValue() == null) {
                continue;
            }
            FollowItem item = new FollowItem();
            item.setTargetId(UUID.fromString(t.getValue()));
            Double score = t.getScore();
            item.setFollowTime(score == null ? null : Instant.ofEpochMilli(score.longValue()));
            items.add(item);
        }
        return items;
    }

    private String followeeKey(UUID userId, int entityType) {
        return "followee:" + userId + ":" + entityType;
    }

    private String followerKey(int entityType, UUID entityId) {
        return "follower:" + entityType + ":" + entityId;
    }
}
