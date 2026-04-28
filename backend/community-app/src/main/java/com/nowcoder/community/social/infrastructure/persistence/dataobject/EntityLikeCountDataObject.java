package com.nowcoder.community.social.infrastructure.persistence.dataobject;

import java.util.UUID;

public class EntityLikeCountDataObject {

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
