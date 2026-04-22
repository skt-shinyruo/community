package com.nowcoder.community.social.like;

import java.util.UUID;

/**
 * internal likes 扫描行：用于 keyset 分页回填下游投影。
 */
public class LikeScanRow {

    private UUID entityId;
    private UUID userId;

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }
}
