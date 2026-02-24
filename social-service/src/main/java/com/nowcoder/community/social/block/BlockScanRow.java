package com.nowcoder.community.social.block;

/**
 * internal blocks 扫描行：用于 keyset 分页回填下游投影。
 */
public class BlockScanRow {

    private int userId;
    private int targetUserId;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(int targetUserId) {
        this.targetUserId = targetUserId;
    }
}

