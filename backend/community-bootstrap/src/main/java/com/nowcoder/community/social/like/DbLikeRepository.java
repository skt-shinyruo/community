package com.nowcoder.community.social.like;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public boolean addLike(int userId, int entityType, int entityId) {
        try {
            return mapper.insertLike(userId, entityType, entityId) > 0;
        } catch (DuplicateKeyException ignored) {
            // 唯一约束冲突视为幂等重复
            return false;
        }
    }

    @Override
    public boolean removeLike(int userId, int entityType, int entityId) {
        return mapper.deleteLike(userId, entityType, entityId) > 0;
    }

    @Override
    public boolean isLiked(int userId, int entityType, int entityId) {
        return mapper.countLike(userId, entityType, entityId) > 0;
    }

    @Override
    public long countEntityLikes(int entityType, int entityId) {
        return mapper.countEntityLikes(entityType, entityId);
    }

    @Override
    public long incrementUserLikeCount(int userId, long delta) {
        if (delta == 0) {
            return getUserLikeCount(userId);
        }
        mapper.incrementUserLikeCount(userId, delta);
        return getUserLikeCount(userId);
    }

    @Override
    public long getUserLikeCount(int userId) {
        Long v = mapper.getUserLikeCount(userId);
        return v == null ? 0 : v;
    }

    @Override
    public Map<Integer, Long> countEntityLikesBatch(int entityType, List<Integer> entityIds) {
        Map<Integer, Long> out = new HashMap<>();
        if (entityIds == null || entityIds.isEmpty()) {
            return out;
        }
        for (Integer id : entityIds) {
            if (id != null && id > 0) {
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
            if (r == null || r.getEntityId() <= 0) {
                continue;
            }
            out.put(r.getEntityId(), Math.max(0, r.getLikeCount()));
        }
        return out;
    }

    @Override
    public Map<Integer, Boolean> likedStatusesBatch(int userId, int entityType, List<Integer> entityIds) {
        Map<Integer, Boolean> out = new HashMap<>();
        if (entityIds == null || entityIds.isEmpty()) {
            return out;
        }
        for (Integer id : entityIds) {
            if (id != null && id > 0) {
                out.put(id, Boolean.FALSE);
            }
        }
        if (out.isEmpty()) {
            return out;
        }
        List<Integer> liked = mapper.selectLikedEntityIds(userId, entityType, entityIds);
        if (liked == null || liked.isEmpty()) {
            return out;
        }
        for (Integer id : liked) {
            if (id != null && id > 0) {
                out.put(id, Boolean.TRUE);
            }
        }
        return out;
    }
}
