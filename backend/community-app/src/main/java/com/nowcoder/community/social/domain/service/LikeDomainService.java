package com.nowcoder.community.social.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.model.ResolvedSocialEntity;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.COMMENT;
import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public class LikeDomainService {

    public void validateLike(UUID actorUserId, int entityType, UUID entityId) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        if (!isSupportedLikeEntityType(entityType) || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType/entityId 非法");
        }
        if (entityType == USER && actorUserId.equals(entityId)) {
            throw new BusinessException(INVALID_ARGUMENT, "不能给自己点赞");
        }
    }

    private boolean isSupportedLikeEntityType(int entityType) {
        return entityType == USER || entityType == POST || entityType == COMMENT;
    }

    public boolean resolveTargetState(boolean existed, Boolean requestedLiked) {
        return requestedLiked == null ? !existed : Boolean.TRUE.equals(requestedLiked);
    }

    public String relationKey(UUID actorUserId, int entityType, UUID entityId) {
        return "like:" + actorUserId + ":" + entityType + ":" + entityId;
    }

    public LikeChangedDomainEvent likeChangedEvent(
            UUID actorUserId,
            int entityType,
            UUID entityId,
            ResolvedSocialEntity resolved,
            boolean liked,
            Instant occurredAt
    ) {
        return new LikeChangedDomainEvent(
                actorUserId,
                entityType,
                entityId,
                resolved == null ? null : resolved.entityUserId(),
                entityType == POST ? entityId : resolved == null ? null : resolved.postId(),
                relationKey(actorUserId, entityType, entityId),
                liked,
                occurredAt
        );
    }
}
