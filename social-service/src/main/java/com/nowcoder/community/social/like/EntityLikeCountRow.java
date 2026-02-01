package com.nowcoder.community.social.like;

public class EntityLikeCountRow {

    private int entityId;
    private long likeCount;

    public int getEntityId() {
        return entityId;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }
}

