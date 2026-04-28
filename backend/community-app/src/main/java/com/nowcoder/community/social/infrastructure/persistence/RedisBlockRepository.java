// Redis 拉黑关系实现：key=block:{userId}，value=set(targetUserId)。
package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.social.domain.model.BlockRelation;
import com.nowcoder.community.social.domain.repository.BlockRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "social.storage", havingValue = "redis")
public class RedisBlockRepository implements BlockRepository {

    private final StringRedisTemplate redisTemplate;

    public RedisBlockRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean block(UUID userId, UUID targetUserId) {
        Long added = redisTemplate.opsForSet().add(key(userId), String.valueOf(targetUserId));
        return added != null && added > 0;
    }

    @Override
    public boolean unblock(UUID userId, UUID targetUserId) {
        Long removed = redisTemplate.opsForSet().remove(key(userId), String.valueOf(targetUserId));
        return removed != null && removed > 0;
    }

    @Override
    public boolean hasBlocked(UUID userId, UUID targetUserId) {
        Boolean member = redisTemplate.opsForSet().isMember(key(userId), String.valueOf(targetUserId));
        return Boolean.TRUE.equals(member);
    }

    @Override
    public List<UUID> listBlockedUserIds(UUID userId) {
        Set<String> members = redisTemplate.opsForSet().members(key(userId));
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        List<UUID> list = new ArrayList<>(members.size());
        for (String s : members) {
            if (s == null) continue;
            try {
                list.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return list;
    }

    @Override
    public List<BlockRelation> scanBlocksAfter(UUID afterUserId, UUID afterTargetUserId, int limit) {
        throw new UnsupportedOperationException("Redis-backed block projection snapshots are not supported; use social.storage=db");
    }

    @Override
    public boolean requiresExplicitCompensation() {
        return true;
    }

    private String key(UUID userId) {
        return "block:" + userId;
    }
}
