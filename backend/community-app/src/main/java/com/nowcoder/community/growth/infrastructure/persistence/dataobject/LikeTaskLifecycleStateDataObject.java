package com.nowcoder.community.growth.infrastructure.persistence.dataobject;

import com.nowcoder.community.growth.domain.model.LikeTaskLifecycleState;

import java.util.UUID;

public class LikeTaskLifecycleStateDataObject {

    private UUID recipientUserId;
    private String relationKey;
    private UUID relationInstanceId;
    private long sourceVersion;
    private Boolean active;
    private String sourceEventId;

    public UUID getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(UUID recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public String getRelationKey() {
        return relationKey;
    }

    public void setRelationKey(String relationKey) {
        this.relationKey = relationKey;
    }

    public UUID getRelationInstanceId() {
        return relationInstanceId;
    }

    public void setRelationInstanceId(UUID relationInstanceId) {
        this.relationInstanceId = relationInstanceId;
    }

    public long getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(long sourceVersion) {
        this.sourceVersion = sourceVersion;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public void setSourceEventId(String sourceEventId) {
        this.sourceEventId = sourceEventId;
    }

    public LikeTaskLifecycleState toDomain() {
        if (active == null) {
            return null;
        }
        return new LikeTaskLifecycleState(
                recipientUserId,
                relationKey,
                relationInstanceId,
                sourceVersion,
                active,
                sourceEventId
        );
    }

    public static LikeTaskLifecycleStateDataObject from(LikeTaskLifecycleState state) {
        LikeTaskLifecycleStateDataObject dataObject = new LikeTaskLifecycleStateDataObject();
        dataObject.setRecipientUserId(state.recipientUserId());
        dataObject.setRelationKey(state.relationKey());
        dataObject.setRelationInstanceId(state.relationInstanceId());
        dataObject.setSourceVersion(state.sourceVersion());
        dataObject.setActive(state.active());
        dataObject.setSourceEventId(state.sourceEventId());
        return dataObject;
    }
}
