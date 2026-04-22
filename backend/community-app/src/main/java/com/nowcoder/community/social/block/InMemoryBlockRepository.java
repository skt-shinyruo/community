// 内存版拉黑关系实现：用于本地/单测场景（非持久化）。
package com.nowcoder.community.social.block;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "social.storage", havingValue = "memory")
public class InMemoryBlockRepository implements BlockRepository {

    private final ConcurrentHashMap<UUID, Set<UUID>> blocks = new ConcurrentHashMap<>();

    @Override
    public boolean block(UUID userId, UUID targetUserId) {
        return blocks.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(targetUserId);
    }

    @Override
    public boolean unblock(UUID userId, UUID targetUserId) {
        Set<UUID> set = blocks.get(userId);
        if (set == null) {
            return false;
        }
        return set.remove(targetUserId);
    }

    @Override
    public boolean hasBlocked(UUID userId, UUID targetUserId) {
        Set<UUID> set = blocks.get(userId);
        return set != null && set.contains(targetUserId);
    }

    @Override
    public List<UUID> listBlockedUserIds(UUID userId) {
        Set<UUID> set = blocks.get(userId);
        if (set == null || set.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(set);
    }
}
