package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.social.domain.repository.LikeRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "social.storage", havingValue = "memory")
public class InMemoryLikeRepository implements LikeRepository {

    private final Map<String, Set<UUID>> entityLikes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> userLikeCounts = new ConcurrentHashMap<>();

    @Override
    public boolean addLike(UUID userId, int entityType, UUID entityId) {
        String key = entityKey(entityType, entityId);
        Set<UUID> set = entityLikes.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet());
        return set.add(userId);
    }

    @Override
    public boolean removeLike(UUID userId, int entityType, UUID entityId) {
        Set<UUID> set = entityLikes.get(entityKey(entityType, entityId));
        if (set == null) {
            return false;
        }
        return set.remove(userId);
    }

    @Override
    public boolean isLiked(UUID userId, int entityType, UUID entityId) {
        Set<UUID> set = entityLikes.get(entityKey(entityType, entityId));
        return set != null && set.contains(userId);
    }

    @Override
    public long countEntityLikes(int entityType, UUID entityId) {
        Set<UUID> set = entityLikes.get(entityKey(entityType, entityId));
        return set == null ? 0 : set.size();
    }

    @Override
    public long incrementUserLikeCount(UUID userId, long delta) {
        return userLikeCounts.merge(userId, delta, Long::sum);
    }

    @Override
    public long getUserLikeCount(UUID userId) {
        return userLikeCounts.getOrDefault(userId, 0L);
    }

    @Override
    public boolean requiresExplicitCompensation() {
        return true;
    }

    private String entityKey(int entityType, UUID entityId) {
        return "like:entity:" + entityType + ":" + entityId;
    }
}
