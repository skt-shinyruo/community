package com.nowcoder.community.social.like;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MySQL 持久化实现：以 DB 为 SSOT（source of truth）。
 *
 * <p>说明：该实现不依赖 Redis 数据存在，Redis 可作为后续缓存/加速层演进。</p>
 */
@Repository
@ConditionalOnProperty(name = "social.storage", havingValue = "db", matchIfMissing = true)
public class DbLikeRepository implements LikeRepository {

    private final LikeMapper mapper;

    public DbLikeRepository(LikeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean addLike(UUID userId, int entityType, UUID entityId) {
        try {
            return mapper.insertLike(userId, entityType, entityId) > 0;
        } catch (DuplicateKeyException ignored) {
            // 唯一约束冲突视为幂等重复
            return false;
        }
    }

    @Override
    public boolean removeLike(UUID userId, int entityType, UUID entityId) {
        return mapper.deleteLike(userId, entityType, entityId) > 0;
    }

    @Override
    public boolean isLiked(UUID userId, int entityType, UUID entityId) {
        return mapper.countLike(userId, entityType, entityId) > 0;
    }

    @Override
    public long countEntityLikes(int entityType, UUID entityId) {
        return mapper.countEntityLikes(entityType, entityId);
    }

    @Override
    public long incrementUserLikeCount(UUID userId, long delta) {
        if (delta == 0) {
            return getUserLikeCount(userId);
        }
        mapper.incrementUserLikeCount(userId, delta);
        return getUserLikeCount(userId);
    }

    @Override
    public long getUserLikeCount(UUID userId) {
        Long v = mapper.getUserLikeCount(userId);
        return v == null ? 0 : v;
    }

    @Override
    public Map<UUID, Long> countEntityLikesBatch(int entityType, List<UUID> entityIds) {
        Map<UUID, Long> out = new HashMap<>();
        if (entityIds == null || entityIds.isEmpty()) {
            return out;
        }
        for (UUID id : entityIds) {
            if (id != null) {
                out.put(id, 0L);
            }
        }
        if (out.isEmpty()) {
            return out;
        }
        List<EntityLikeCountRow> rows = mapper.countEntityLikesByEntityIds(entityType, entityIds);
        if (rows == null || rows.isEmpty()) {
            return out;
        }
        for (EntityLikeCountRow r : rows) {
            if (r == null || r.getEntityId() == null) {
                continue;
            }
            out.put(r.getEntityId(), Math.max(0, r.getLikeCount()));
        }
        return out;
    }

    @Override
    public Map<UUID, Boolean> likedStatusesBatch(UUID userId, int entityType, List<UUID> entityIds) {
        Map<UUID, Boolean> out = new HashMap<>();
        if (entityIds == null || entityIds.isEmpty()) {
            return out;
        }
        for (UUID id : entityIds) {
            if (id != null) {
                out.put(id, Boolean.FALSE);
            }
        }
        if (out.isEmpty()) {
            return out;
        }
        List<UUID> liked = mapper.selectLikedEntityIds(userId, entityType, entityIds);
        if (liked == null || liked.isEmpty()) {
            return out;
        }
        for (UUID id : liked) {
            if (id != null) {
                out.put(id, Boolean.TRUE);
            }
        }
        return out;
    }
}
