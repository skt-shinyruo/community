package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.social.domain.model.LikeRelation;
import com.nowcoder.community.social.domain.repository.LikeRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "social.storage", havingValue = "memory")
public class InMemoryLikeRepository implements LikeRepository {

    private static final UUID UNKNOWN_OWNER = new UUID(0L, 0L);

    private final Map<String, Map<UUID, UUID>> entityLikes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> userLikeCounts = new ConcurrentHashMap<>();

    @Override
    public boolean addLike(UUID userId, int entityType, UUID entityId) {
        return addLike(userId, entityType, entityId, null);
    }

    @Override
    public boolean addLike(UUID userId, int entityType, UUID entityId, UUID entityUserId) {
        String key = entityKey(entityType, entityId);
        Map<UUID, UUID> map = entityLikes.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
        return map.putIfAbsent(userId, entityUserId == null ? UNKNOWN_OWNER : entityUserId) == null;
    }

    @Override
    public boolean removeLike(UUID userId, int entityType, UUID entityId) {
        Map<UUID, UUID> map = entityLikes.get(entityKey(entityType, entityId));
        if (map == null) {
            return false;
        }
        return map.remove(userId) != null;
    }

    @Override
    public Optional<LikeRelation> findLike(UUID userId, int entityType, UUID entityId) {
        Map<UUID, UUID> map = entityLikes.get(entityKey(entityType, entityId));
        if (map == null || !map.containsKey(userId)) {
            return Optional.empty();
        }
        UUID ownerUserId = map.get(userId);
        return Optional.of(new LikeRelation(userId, entityType, entityId, UNKNOWN_OWNER.equals(ownerUserId) ? null : ownerUserId));
    }

    @Override
    public long deleteLikesByEntity(int entityType, UUID entityId) {
        Map<UUID, UUID> removed = entityLikes.remove(entityKey(entityType, entityId));
        if (removed == null || removed.isEmpty()) {
            return 0;
        }
        for (UUID ownerUserId : removed.values()) {
            if (ownerUserId != null && !UNKNOWN_OWNER.equals(ownerUserId)) {
                incrementUserLikeCount(ownerUserId, -1);
            }
        }
        return removed.size();
    }

    @Override
    public boolean isLiked(UUID userId, int entityType, UUID entityId) {
        Map<UUID, UUID> map = entityLikes.get(entityKey(entityType, entityId));
        return map != null && map.containsKey(userId);
    }

    @Override
    public long countEntityLikes(int entityType, UUID entityId) {
        Map<UUID, UUID> map = entityLikes.get(entityKey(entityType, entityId));
        return map == null ? 0 : map.size();
    }

    @Override
    public long incrementUserLikeCount(UUID userId, long delta) {
        return userLikeCounts.merge(userId, Math.max(0, delta), (current, ignored) -> Math.max(0, current + delta));
    }

    @Override
    public long resetUserLikeCount(UUID userId, long likeCount) {
        long normalized = Math.max(0, likeCount);
        userLikeCounts.put(userId, normalized);
        return normalized;
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
