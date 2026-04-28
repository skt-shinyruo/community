// 内存版拉黑关系实现：用于本地/单测场景（非持久化）。
package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.social.domain.model.BlockRelation;
import com.nowcoder.community.social.domain.repository.BlockRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
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

    @Override
    public List<BlockRelation> scanBlocksAfter(UUID afterUserId, UUID afterTargetUserId, int limit) {
        int normalizedLimit = Math.min(500, Math.max(1, limit));
        UUID normalizedAfterUserId = afterUserId == null ? new UUID(0L, 0L) : afterUserId;
        UUID normalizedAfterTargetUserId = afterTargetUserId == null ? new UUID(0L, 0L) : afterTargetUserId;

        List<UUID> blockerIds = blocks.keySet().stream()
                .sorted()
                .toList();

        List<BlockRelation> rows = new ArrayList<>();
        for (UUID blockerId : blockerIds) {
            List<UUID> blockedIds = blocks.getOrDefault(blockerId, Set.of()).stream()
                    .sorted(Comparator.naturalOrder())
                    .toList();
            for (UUID blockedId : blockedIds) {
                if (blockerId.compareTo(normalizedAfterUserId) < 0) {
                    continue;
                }
                if (blockerId.equals(normalizedAfterUserId) && blockedId.compareTo(normalizedAfterTargetUserId) <= 0) {
                    continue;
                }
                rows.add(new BlockRelation(blockerId, blockedId));
                if (rows.size() >= normalizedLimit) {
                    return rows;
                }
            }
        }
        return rows;
    }

    @Override
    public boolean requiresExplicitCompensation() {
        return true;
    }
}
