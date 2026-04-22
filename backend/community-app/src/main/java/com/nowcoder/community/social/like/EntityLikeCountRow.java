package com.nowcoder.community.social.like;

import java.util.UUID;

public class EntityLikeCountRow {

    private UUID entityId;
    private long likeCount;

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }
}
