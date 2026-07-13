// 拉黑关系存储抽象：用于实现“拉黑/解除拉黑/查询关系/列表”能力（MVP 以 Redis 为主）。
package com.nowcoder.community.social.domain.repository;

import com.nowcoder.community.social.domain.model.BlockRelation;

import java.util.List;
import java.util.UUID;

public interface BlockRepository {

    boolean block(UUID userId, UUID targetUserId, long version);

    boolean unblock(UUID userId, UUID targetUserId, long version);

    default boolean block(UUID userId, UUID targetUserId) {
        return block(userId, targetUserId, nextBlockProjectionVersion());
    }

    default boolean unblock(UUID userId, UUID targetUserId) {
        return unblock(userId, targetUserId, nextBlockProjectionVersion());
    }

    boolean hasBlocked(UUID userId, UUID targetUserId);

    List<UUID> listBlockedUserIds(UUID userId);

    List<BlockRelation> scanBlocksAfter(UUID afterUserId, UUID afterTargetUserId, int limit);

    long nextBlockProjectionVersion();

    long currentBlockProjectionVersion();

    default boolean requiresExplicitCompensation() {
        return false;
    }
}
