package com.nowcoder.community.social.block;

import java.util.UUID;

/**
 * internal blocks 扫描行：用于 keyset 分页回填下游投影。
 */
public class BlockScanRow {

    private UUID userId;
    private UUID targetUserId;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(UUID targetUserId) {
        this.targetUserId = targetUserId;
    }
}
