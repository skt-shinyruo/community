// 拉黑关系存储抽象：用于实现“拉黑/解除拉黑/查询关系/列表”能力（MVP 以 Redis 为主）。
package com.nowcoder.community.social.block;

import java.util.List;
import java.util.UUID;

public interface BlockRepository {

    boolean block(UUID userId, UUID targetUserId);

    boolean unblock(UUID userId, UUID targetUserId);

    boolean hasBlocked(UUID userId, UUID targetUserId);

    List<UUID> listBlockedUserIds(UUID userId);
}
