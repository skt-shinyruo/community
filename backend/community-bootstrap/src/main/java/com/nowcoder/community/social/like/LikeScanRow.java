package com.nowcoder.community.social.like;

/**
 * internal likes 扫描行：用于 keyset 分页回填下游投影。
 */
public class LikeScanRow {

    private long entityId;
    private long userId;

    public long getEntityId() {
        return entityId;
    }

    public void setEntityId(long entityId) {
        this.entityId = entityId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }
}

