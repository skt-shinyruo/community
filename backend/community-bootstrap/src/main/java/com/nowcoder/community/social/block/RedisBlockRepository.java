// Redis 拉黑关系实现：key=block:{userId}，value=set(targetUserId)。
package com.nowcoder.community.social.block;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
@ConditionalOnProperty(name = "social.storage", havingValue = "redis")
public class RedisBlockRepository implements BlockRepository {

    private final StringRedisTemplate redisTemplate;

    public RedisBlockRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean block(int userId, int targetUserId) {
        Long added = redisTemplate.opsForSet().add(key(userId), String.valueOf(targetUserId));
        return added != null && added > 0;
    }

    @Override
    public boolean unblock(int userId, int targetUserId) {
        Long removed = redisTemplate.opsForSet().remove(key(userId), String.valueOf(targetUserId));
        return removed != null && removed > 0;
    }

    @Override
    public boolean hasBlocked(int userId, int targetUserId) {
        Boolean member = redisTemplate.opsForSet().isMember(key(userId), String.valueOf(targetUserId));
        return Boolean.TRUE.equals(member);
    }

    @Override
    public List<Integer> listBlockedUserIds(int userId) {
        Set<String> members = redisTemplate.opsForSet().members(key(userId));
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        List<Integer> list = new ArrayList<>(members.size());
        for (String s : members) {
            if (s == null) continue;
            try {
                int id = Integer.parseInt(s);
                if (id > 0) {
                    list.add(id);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return list;
    }

    private String key(int userId) {
        return "block:" + userId;
    }
}
