package com.nowcoder.community.social.like;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

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
        } catch (DataAccessException ignored) {
            // 唯一约束冲突视为幂等重复
            return false;
        }
    }

    @Override
    public boolean removeLike(int userId, int entityType, int entityId) {
        try {
            return mapper.deleteLike(userId, entityType, entityId) > 0;
        } catch (DataAccessException ignored) {
            return false;
        }
    }

    @Override
    public boolean isLiked(int userId, int entityType, int entityId) {
        try {
            return mapper.countLike(userId, entityType, entityId) > 0;
        } catch (DataAccessException ignored) {
            return false;
        }
    }

    @Override
    public long countEntityLikes(int entityType, int entityId) {
        try {
            return mapper.countEntityLikes(entityType, entityId);
        } catch (DataAccessException ignored) {
            return 0;
        }
    }

    @Override
    public long incrementUserLikeCount(int userId, long delta) {
        if (delta == 0) {
            return getUserLikeCount(userId);
        }
        try {
            mapper.incrementUserLikeCount(userId, delta);
        } catch (DataAccessException ignored) {
            // ignore
        }
        return getUserLikeCount(userId);
    }

    @Override
    public long getUserLikeCount(int userId) {
        try {
            Long v = mapper.getUserLikeCount(userId);
            return v == null ? 0 : v;
        } catch (DataAccessException ignored) {
            return 0;
        }
    }
}
