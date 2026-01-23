// 内存版拉黑关系实现：用于本地/单测场景（非持久化）。
package com.nowcoder.community.social.block;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "social.storage", havingValue = "memory")
public class InMemoryBlockRepository implements BlockRepository {

    private final ConcurrentHashMap<Integer, Set<Integer>> blocks = new ConcurrentHashMap<>();

    @Override
    public boolean block(int userId, int targetUserId) {
        return blocks.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(targetUserId);
    }

    @Override
    public boolean unblock(int userId, int targetUserId) {
        Set<Integer> set = blocks.get(userId);
        if (set == null) {
            return false;
        }
        return set.remove(targetUserId);
    }

    @Override
    public boolean hasBlocked(int userId, int targetUserId) {
        Set<Integer> set = blocks.get(userId);
        return set != null && set.contains(targetUserId);
    }

    @Override
    public List<Integer> listBlockedUserIds(int userId) {
        Set<Integer> set = blocks.get(userId);
        if (set == null || set.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(set);
    }
}

