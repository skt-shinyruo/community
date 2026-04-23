package com.nowcoder.community.social.block;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * MySQL 持久化实现：以 DB 为 SSOT（source of truth）。
 */
@Repository
@ConditionalOnProperty(name = "social.storage", havingValue = "db", matchIfMissing = true)
public class DbBlockRepository implements BlockRepository {

    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private final BlockMapper mapper;

    public DbBlockRepository(BlockMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean block(UUID userId, UUID targetUserId) {
        try {
            return mapper.insertBlock(userId, targetUserId) > 0;
        } catch (DuplicateKeyException ignored) {
            // 幂等：重复拉黑视为 false
            return false;
        }
    }

    @Override
    public boolean unblock(UUID userId, UUID targetUserId) {
        return mapper.deleteBlock(userId, targetUserId) > 0;
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
    public List<BlockScanRow> scanBlocksAfter(UUID afterUserId, UUID afterTargetUserId, int limit) {
        UUID normalizedAfterUserId = afterUserId == null ? ZERO_UUID : afterUserId;
        UUID normalizedAfterTargetUserId = afterTargetUserId == null ? ZERO_UUID : afterTargetUserId;
        int normalizedLimit = Math.min(500, Math.max(1, limit));
        List<BlockScanRow> rows = mapper.scanBlocks(normalizedAfterUserId, normalizedAfterTargetUserId, normalizedLimit);
        return rows == null ? List.of() : rows;
    }
}
