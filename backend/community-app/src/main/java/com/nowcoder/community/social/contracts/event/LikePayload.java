package com.nowcoder.community.social.contracts.event;

import java.time.Instant;
import java.util.UUID;

public class LikePayload {

    private UUID actorUserId;
    private int entityType;
    private UUID entityId;
    private UUID entityUserId;
    private UUID postId;
    private String relationKey;
    private Instant occurredAt;
    private Instant createTime;

    public UUID getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(UUID actorUserId) {
        this.actorUserId = actorUserId;
    }

    public int getEntityType() {
        return entityType;
    }

    public void setEntityType(int entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public UUID getEntityUserId() {
        return entityUserId;
    }

    public void setEntityUserId(UUID entityUserId) {
        this.entityUserId = entityUserId;
    }

    public UUID getPostId() {
        return postId;
    }

    public void setPostId(UUID postId) {
        this.postId = postId;
    }

    public String getRelationKey() {
        return relationKey;
    }

    public void setRelationKey(String relationKey) {
        this.relationKey = relationKey;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Instant createTime) {
        this.createTime = createTime;
    }
}
