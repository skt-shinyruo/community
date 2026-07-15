package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.social.domain.model.FollowRelation;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.domain.repository.FollowRepository;
import com.nowcoder.community.social.infrastructure.persistence.dataobject.FollowRelationDataObject;
import com.nowcoder.community.social.infrastructure.persistence.mapper.FollowMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

/**
 * MySQL 持久化实现：以 DB 为 SSOT（source of truth）。
 */
@Repository
public class MyBatisFollowRepository implements FollowRepository {

    private final FollowMapper mapper;

    public MyBatisFollowRepository(FollowMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean follow(UUID userId, int entityType, UUID entityId, long followTimeMillis) {
        Date createdAt = followTimeMillis > 0 ? new Date(followTimeMillis) : new Date();
        try {
            return mapper.insertFollow(userId, entityType, entityId, createdAt) > 0;
        } catch (DuplicateKeyException ignored) {
            // 幂等：重复关注视为 false
            return false;
        }
    }

    @Override
    public boolean unfollow(UUID userId, int entityType, UUID entityId) {
        return mapper.deleteFollow(userId, entityType, entityId) > 0;
    }

    @Override
    public boolean hasFollowed(UUID userId, int entityType, UUID entityId) {
        return mapper.countFollow(userId, entityType, entityId) > 0;
    }

    @Override
    public long countFollowees(UUID userId, int entityType) {
        return mapper.countFollowees(userId, entityType);
    }

    @Override
    public long countFollowers(int entityType, UUID entityId) {
        return mapper.countFollowers(entityType, entityId);
    }

    @Override
    public long countFolloweesExcludingBlocked(UUID userId, int entityType, BlockRepository blockRepository) {
        return mapper.countFolloweesExcludingBlocked(userId, entityType);
    }

    @Override
    public long countFollowersExcludingBlocked(int entityType, UUID entityId, BlockRepository blockRepository) {
        return mapper.countFollowersExcludingBlocked(entityType, entityId);
    }

    @Override
    public List<FollowRelation> listFollowees(UUID userId, int entityType, int offset, int limit) {
        return mapRows(mapper.listFollowees(userId, entityType, offset, limit));
    }

    @Override
    public List<FollowRelation> listFollowers(int entityType, UUID entityId, int offset, int limit) {
        return mapRows(mapper.listFollowers(entityType, entityId, offset, limit));
    }

    @Override
    public List<UUID> listFolloweeIds(UUID userId, int entityType, int limit) {
        return mapper.listFolloweeIds(userId, entityType, Math.min(200, Math.max(1, limit)));
    }

    @Override
    public List<UUID> listFolloweeIdsExcludingBlocked(
            UUID userId,
            int entityType,
            BlockRepository blockRepository,
            int limit
    ) {
        return mapper.listFolloweeIds(userId, entityType, Math.min(200, Math.max(1, limit)));
    }

    @Override
    public List<FollowRelation> listFolloweesExcludingBlocked(
            UUID userId,
            int entityType,
            BlockRepository blockRepository,
            int offset,
            int limit
    ) {
        return mapRows(mapper.listFolloweesExcludingBlocked(userId, entityType, offset, limit));
    }

    @Override
    public List<FollowRelation> listFollowersExcludingBlocked(
            int entityType,
            UUID entityId,
            BlockRepository blockRepository,
            int offset,
            int limit
    ) {
        return mapRows(mapper.listFollowersExcludingBlocked(entityType, entityId, offset, limit));
    }

    private List<FollowRelation> mapRows(List<FollowRelationDataObject> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(Objects::nonNull)
                .map(this::toRelation)
                .toList();
    }

    private FollowRelation toRelation(FollowRelationDataObject row) {
        Date t = row.getFollowTime();
        return new FollowRelation(row.getTargetId(), t == null ? null : Instant.ofEpochMilli(t.getTime()));
    }
}
