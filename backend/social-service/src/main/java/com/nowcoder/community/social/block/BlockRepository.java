// 拉黑关系存储抽象：用于实现“拉黑/解除拉黑/查询关系/列表”能力（MVP 以 Redis 为主）。
package com.nowcoder.community.social.block;

import java.util.List;

public interface BlockRepository {

    boolean block(int userId, int targetUserId);

    boolean unblock(int userId, int targetUserId);

    boolean hasBlocked(int userId, int targetUserId);

    List<Integer> listBlockedUserIds(int userId);
}

