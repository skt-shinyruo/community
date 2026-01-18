package com.nowcoder.community.social.follow;

import com.nowcoder.community.social.follow.dto.FollowItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
@ConditionalOnProperty(name = "social.storage", havingValue = "redis", matchIfMissing = true)
public class RedisFollowRepository implements FollowRepository {

    private final StringRedisTemplate redisTemplate;

    public RedisFollowRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean follow(int userId, int entityType, int entityId, long followTimeMillis) {
        String followeeKey = followeeKey(userId, entityType);
        Double existed = redisTemplate.opsForZSet().score(followeeKey, String.valueOf(entityId));
        if (existed != null) {
            return false;
        }
        redisTemplate.opsForZSet().add(followeeKey, String.valueOf(entityId), followTimeMillis);
        redisTemplate.opsForZSet().add(followerKey(entityType, entityId), String.valueOf(userId), followTimeMillis);
        return true;
    }

    @Override
    public boolean unfollow(int userId, int entityType, int entityId) {
        Long removed = redisTemplate.opsForZSet().remove(followeeKey(userId, entityType), String.valueOf(entityId));
        redisTemplate.opsForZSet().remove(followerKey(entityType, entityId), String.valueOf(userId));
        return removed != null && removed > 0;
    }

    @Override
    public boolean hasFollowed(int userId, int entityType, int entityId) {
        Double score = redisTemplate.opsForZSet().score(followeeKey(userId, entityType), String.valueOf(entityId));
        return score != null;
    }

    @Override
    public long countFollowees(int userId, int entityType) {
        Long size = redisTemplate.opsForZSet().zCard(followeeKey(userId, entityType));
        return size == null ? 0 : size;
    }

    @Override
    public long countFollowers(int entityType, int entityId) {
        Long size = redisTemplate.opsForZSet().zCard(followerKey(entityType, entityId));
        return size == null ? 0 : size;
    }

    @Override
    public List<FollowItem> listFollowees(int userId, int entityType, int offset, int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(
                followeeKey(userId, entityType),
                offset,
                offset + limit - 1L
        );
        return toItems(tuples);
    }

    @Override
    public List<FollowItem> listFollowers(int entityType, int entityId, int offset, int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(
                followerKey(entityType, entityId),
                offset,
                offset + limit - 1L
        );
        return toItems(tuples);
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
            item.setTargetId(Integer.parseInt(t.getValue()));
            Double score = t.getScore();
            item.setFollowTime(score == null ? null : Instant.ofEpochMilli(score.longValue()));
            items.add(item);
        }
        return items;
    }

    private String followeeKey(int userId, int entityType) {
        return "followee:" + userId + ":" + entityType;
    }

    private String followerKey(int entityType, int entityId) {
        return "follower:" + entityType + ":" + entityId;
    }
}

