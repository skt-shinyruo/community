package com.nowcoder.community.notice.infrastructure.persistence.dataobject;

import com.nowcoder.community.notice.domain.model.LikeNoticeProjectionState;

import java.util.UUID;

public class LikeNoticeProjectionStateDataObject {

    private UUID recipientUserId;
    private String sourceRelationKey;
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

    public String getSourceRelationKey() {
        return sourceRelationKey;
    }

    public void setSourceRelationKey(String sourceRelationKey) {
        this.sourceRelationKey = sourceRelationKey;
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

    public LikeNoticeProjectionState toDomain() {
        if (active == null) {
            return null;
        }
        return new LikeNoticeProjectionState(
                recipientUserId,
                sourceRelationKey,
                relationInstanceId,
                sourceVersion,
                active,
                sourceEventId
        );
    }

    public static LikeNoticeProjectionStateDataObject from(LikeNoticeProjectionState state) {
        LikeNoticeProjectionStateDataObject dataObject = new LikeNoticeProjectionStateDataObject();
        dataObject.setRecipientUserId(state.recipientUserId());
        dataObject.setSourceRelationKey(state.sourceRelationKey());
        dataObject.setRelationInstanceId(state.relationInstanceId());
        dataObject.setSourceVersion(state.sourceVersion());
        dataObject.setActive(state.active());
        dataObject.setSourceEventId(state.sourceEventId());
        return dataObject;
    }
}
