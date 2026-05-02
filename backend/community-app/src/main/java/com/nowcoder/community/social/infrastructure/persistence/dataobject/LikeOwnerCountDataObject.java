package com.nowcoder.community.social.infrastructure.persistence.dataobject;

import java.util.UUID;

public class LikeOwnerCountDataObject {

    private UUID entityUserId;
    private long likeCount;

    public UUID getEntityUserId() {
        return entityUserId;
    }

    public void setEntityUserId(UUID entityUserId) {
        this.entityUserId = entityUserId;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }
}
