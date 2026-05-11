package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.social.domain.model.FollowRelation;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.domain.repository.FollowRepository;
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

            if followeeScore or followerScore then
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

            local removedFollowee = redis.call('ZREM', followeeKey, entityId)
            local removedFollower = redis.call('ZREM', followerKey, userId)

            if (removedFollowee and removedFollowee > 0) or (removedFollower and removedFollower > 0) then
              return 1
            end
            return 0
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    private static final int FILTER_SCAN_BATCH_SIZE = 100;

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
    public List<FollowRelation> listFollowees(UUID userId, int entityType, int offset, int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(
                followeeKey(userId, entityType),
                offset,
                (long) offset + (long) limit - 1L
        );
        return toItems(tuples);
    }

    @Override
    public List<FollowRelation> listFollowers(int entityType, UUID entityId, int offset, int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(
                followerKey(entityType, entityId),
                offset,
                (long) offset + (long) limit - 1L
        );
        return toItems(tuples);
    }

    @Override
    public long countFolloweesExcludingBlocked(UUID userId, int entityType, BlockRepository blockRepository) {
        return countFiltered(followeeKey(userId, entityType), userId, blockRepository);
    }

    @Override
    public long countFollowersExcludingBlocked(int entityType, UUID entityId, BlockRepository blockRepository) {
        return countFiltered(followerKey(entityType, entityId), entityId, blockRepository);
    }

    @Override
    public List<FollowRelation> listFolloweesExcludingBlocked(
            UUID userId,
            int entityType,
            BlockRepository blockRepository,
            int offset,
            int limit
    ) {
        return listFiltered(followeeKey(userId, entityType), userId, blockRepository, offset, limit);
    }

    @Override
    public List<FollowRelation> listFollowersExcludingBlocked(
            int entityType,
            UUID entityId,
            BlockRepository blockRepository,
            int offset,
            int limit
    ) {
        return listFiltered(followerKey(entityType, entityId), entityId, blockRepository, offset, limit);
    }

    @Override
    public boolean requiresExplicitCompensation() {
        return true;
    }

    private List<FollowRelation> toItems(Set<ZSetOperations.TypedTuple<String>> tuples) {
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<FollowRelation> items = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            if (t == null || t.getValue() == null) {
                continue;
            }
            Double score = t.getScore();
            items.add(new FollowRelation(
                    UUID.fromString(t.getValue()),
                    score == null ? null : Instant.ofEpochMilli(score.longValue())
            ));
        }
        return items;
    }

    private long countFiltered(String key, UUID viewerUserId, BlockRepository blockRepository) {
        long count = 0;
        int cursor = 0;
        while (true) {
            Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(
                    key,
                    cursor,
                    (long) cursor + FILTER_SCAN_BATCH_SIZE - 1L
            );
            if (tuples == null || tuples.isEmpty()) {
                break;
            }
            for (FollowRelation relation : toItems(tuples)) {
                if (!isEitherBlocked(viewerUserId, relation.targetId(), blockRepository)) {
                    count++;
                }
            }
            if (tuples.size() < FILTER_SCAN_BATCH_SIZE) {
                break;
            }
            cursor += FILTER_SCAN_BATCH_SIZE;
        }
        return count;
    }

    private List<FollowRelation> listFiltered(String key, UUID viewerUserId, BlockRepository blockRepository, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(0, limit);
        if (safeLimit == 0) {
            return List.of();
        }
        List<FollowRelation> result = new ArrayList<>(safeLimit);
        int accepted = 0;
        int cursor = 0;
        while (result.size() < safeLimit) {
            Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(
                    key,
                    cursor,
                    (long) cursor + FILTER_SCAN_BATCH_SIZE - 1L
            );
            if (tuples == null || tuples.isEmpty()) {
                break;
            }
            for (FollowRelation relation : toItems(tuples)) {
                if (!isEitherBlocked(viewerUserId, relation.targetId(), blockRepository)) {
                    if (accepted >= safeOffset) {
                        result.add(relation);
                        if (result.size() >= safeLimit) {
                            break;
                        }
                    }
                    accepted++;
                }
            }
            if (tuples.size() < FILTER_SCAN_BATCH_SIZE) {
                break;
            }
            cursor += FILTER_SCAN_BATCH_SIZE;
        }
        return result;
    }

    private boolean isEitherBlocked(UUID userIdA, UUID userIdB, BlockRepository blockRepository) {
        if (userIdA == null || userIdB == null || userIdA.equals(userIdB) || blockRepository == null) {
            return false;
        }
        return blockRepository.hasBlocked(userIdA, userIdB) || blockRepository.hasBlocked(userIdB, userIdA);
    }

    private String followeeKey(UUID userId, int entityType) {
        return "followee:" + userId + ":" + entityType;
    }

    private String followerKey(int entityType, UUID entityId) {
        return "follower:" + entityType + ":" + entityId;
    }
}
