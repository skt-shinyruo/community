package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.social.domain.model.LikeRelation;
import com.nowcoder.community.social.domain.repository.LikeRepository;
import com.nowcoder.community.social.infrastructure.persistence.dataobject.EntityLikeCountDataObject;
import com.nowcoder.community.social.infrastructure.persistence.dataobject.LikeOwnerCountDataObject;
import com.nowcoder.community.social.infrastructure.persistence.dataobject.LikeScanDataObject;
import com.nowcoder.community.social.infrastructure.persistence.mapper.LikeMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * MySQL 持久化实现：以 DB 为 SSOT（source of truth）。
 *
 * <p>说明：该实现不依赖 Redis 数据存在，Redis 可作为后续缓存/加速层演进。</p>
 */
@Repository
public class MyBatisLikeRepository implements LikeRepository {

    private final LikeMapper mapper;

    public MyBatisLikeRepository(LikeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean addLike(UUID userId, int entityType, UUID entityId) {
        return addLike(userId, entityType, entityId, null);
    }

    @Override
    public boolean addLike(UUID userId, int entityType, UUID entityId, UUID entityUserId) {
        try {
            return mapper.insertLike(userId, entityType, entityId, entityUserId) > 0;
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
    public Optional<LikeRelation> findLike(UUID userId, int entityType, UUID entityId) {
        LikeScanDataObject row = mapper.selectLike(userId, entityType, entityId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new LikeRelation(row.getUserId(), entityType, row.getEntityId(), row.getEntityUserId()));
    }

    @Override
    public long deleteLikesByEntity(int entityType, UUID entityId) {
        List<LikeOwnerCountDataObject> ownerCounts = mapper.countLikeOwnersByEntity(entityType, entityId);
        int deleted = mapper.deleteLikesByEntity(entityType, entityId);
        if (deleted <= 0 || ownerCounts == null || ownerCounts.isEmpty()) {
            return deleted;
        }
        for (LikeOwnerCountDataObject ownerCount : ownerCounts) {
            if (ownerCount == null || ownerCount.getEntityUserId() == null || ownerCount.getLikeCount() <= 0) {
                continue;
            }
            long current = getUserLikeCount(ownerCount.getEntityUserId());
            mapper.resetUserLikeCount(ownerCount.getEntityUserId(), Math.max(0, current - ownerCount.getLikeCount()));
        }
        return deleted;
    }

    @Override
    public List<LikeRelation> scanLikesByEntity(int entityType, UUID entityId, UUID afterActorUserId, int limit) {
        UUID cursor = afterActorUserId == null ? new UUID(0L, 0L) : afterActorUserId;
        List<LikeScanDataObject> rows = mapper.scanLikesByEntity(entityType, entityId, cursor, limit);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new LikeRelation(row.getUserId(), entityType, row.getEntityId(), row.getEntityUserId()))
                .toList();
    }

    @Override
    public List<UUID> scanTargetIdsAfter(int entityType, UUID afterEntityId, int limit) {
        UUID cursor = afterEntityId == null ? new UUID(0L, 0L) : afterEntityId;
        if (limit <= 0) {
            return List.of();
        }
        List<UUID> targetIds = mapper.scanTargetIdsAfter(entityType, cursor, limit);
        return targetIds == null ? List.of() : targetIds;
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
    public long resetUserLikeCount(UUID userId, long likeCount) {
        long normalized = Math.max(0, likeCount);
        mapper.resetUserLikeCount(userId, normalized);
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
        List<EntityLikeCountDataObject> rows = mapper.countEntityLikesByEntityIds(entityType, entityIds);
        if (rows == null || rows.isEmpty()) {
            return out;
        }
        for (EntityLikeCountDataObject r : rows) {
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
