package com.nowcoder.community.social.like;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "social.storage", havingValue = "memory")
public class InMemoryLikeRepository implements LikeRepository {

    private final Map<String, Set<Integer>> entityLikes = new ConcurrentHashMap<>();
    private final Map<Integer, Long> userLikeCounts = new ConcurrentHashMap<>();

    @Override
    public boolean addLike(int userId, int entityType, int entityId) {
        String key = entityKey(entityType, entityId);
        Set<Integer> set = entityLikes.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet());
        return set.add(userId);
    }

    @Override
    public boolean removeLike(int userId, int entityType, int entityId) {
        Set<Integer> set = entityLikes.get(entityKey(entityType, entityId));
        if (set == null) {
            return false;
        }
        return set.remove(userId);
    }

    @Override
    public boolean isLiked(int userId, int entityType, int entityId) {
        Set<Integer> set = entityLikes.get(entityKey(entityType, entityId));
        return set != null && set.contains(userId);
    }

    @Override
    public long countEntityLikes(int entityType, int entityId) {
        Set<Integer> set = entityLikes.get(entityKey(entityType, entityId));
        return set == null ? 0 : set.size();
    }

    @Override
    public long incrementUserLikeCount(int userId, long delta) {
        return userLikeCounts.merge(userId, delta, Long::sum);
    }

    @Override
    public long getUserLikeCount(int userId) {
        return userLikeCounts.getOrDefault(userId, 0L);
    }

    @Override
    public boolean requiresExplicitCompensation() {
        return true;
    }

    private String entityKey(int entityType, int entityId) {
        return "like:entity:" + entityType + ":" + entityId;
    }
}
