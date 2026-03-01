package com.nowcoder.community.social.block;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MySQL 持久化实现：以 DB 为 SSOT（source of truth）。
 */
@Repository
@ConditionalOnProperty(name = "social.storage", havingValue = "db", matchIfMissing = true)
public class DbBlockRepository implements BlockRepository {

    private final BlockMapper mapper;

    public DbBlockRepository(BlockMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean block(int userId, int targetUserId) {
        try {
            return mapper.insertBlock(userId, targetUserId) > 0;
        } catch (DuplicateKeyException ignored) {
            // 幂等：重复拉黑视为 false
            return false;
        }
    }

    @Override
    public boolean unblock(int userId, int targetUserId) {
        return mapper.deleteBlock(userId, targetUserId) > 0;
    }

    @Override
    public boolean hasBlocked(int userId, int targetUserId) {
        return mapper.countBlock(userId, targetUserId) > 0;
    }

    @Override
    public List<Integer> listBlockedUserIds(int userId) {
        List<Integer> list = mapper.listBlockedUserIds(userId);
        return list == null ? List.of() : list;
    }
}
