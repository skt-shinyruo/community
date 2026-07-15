package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.social.domain.model.BlockRelation;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.infrastructure.persistence.dataobject.BlockRelationDataObject;
import com.nowcoder.community.social.infrastructure.persistence.mapper.BlockMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * MySQL 持久化实现：以 DB 为 SSOT（source of truth）。
 */
@Repository
public class MyBatisBlockRepository implements BlockRepository {

    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final int BLOCK_VERSION_COUNTER_ID = 1;

    private final BlockMapper mapper;

    public MyBatisBlockRepository(BlockMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean block(UUID userId, UUID targetUserId, long version) {
        try {
            boolean changed = mapper.insertBlock(userId, targetUserId, version) > 0;
            if (changed) {
                mapper.insertVersionLog(version, userId, targetUserId, true);
            }
            return changed;
        } catch (DuplicateKeyException ignored) {
            // 幂等：重复拉黑视为 false
            return false;
        }
    }

    @Override
    public boolean unblock(UUID userId, UUID targetUserId, long version) {
        boolean changed = mapper.deleteBlock(userId, targetUserId) > 0;
        if (changed) {
            mapper.insertVersionLog(version, userId, targetUserId, false);
        }
        return changed;
    }

    @Override
    public boolean hasBlocked(UUID userId, UUID targetUserId) {
        return mapper.countBlock(userId, targetUserId) > 0;
    }

    @Override
    public List<UUID> listBlockedUserIds(UUID userId) {
        List<UUID> list = mapper.listBlockedUserIds(userId);
        return list == null ? List.of() : list;
    }

    @Override
    public List<BlockRelation> scanBlocksAfter(UUID afterUserId, UUID afterTargetUserId, int limit) {
        UUID normalizedAfterUserId = afterUserId == null ? ZERO_UUID : afterUserId;
        UUID normalizedAfterTargetUserId = afterTargetUserId == null ? ZERO_UUID : afterTargetUserId;
        int normalizedLimit = Math.min(500, Math.max(1, limit));
        List<BlockRelationDataObject> rows = mapper.scanBlocks(normalizedAfterUserId, normalizedAfterTargetUserId, normalizedLimit);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new BlockRelation(row.getUserId(), row.getTargetUserId(), row.getVersion()))
                .toList();
    }

    @Override
    public long nextBlockProjectionVersion() {
        mapper.upsertVersionCounter(BLOCK_VERSION_COUNTER_ID);
        long current = mapper.selectVersionCounterForUpdate(BLOCK_VERSION_COUNTER_ID);
        long next = current + 1L;
        mapper.updateVersionCounter(BLOCK_VERSION_COUNTER_ID, next);
        return next;
    }

    @Override
    public long currentBlockProjectionVersion() {
        mapper.upsertVersionCounter(BLOCK_VERSION_COUNTER_ID);
        return mapper.selectVersionCounter(BLOCK_VERSION_COUNTER_ID);
    }

}
