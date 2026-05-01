package com.nowcoder.community.social.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.model.ResolvedSocialEntity;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public class LikeDomainService {

    public void validateLike(UUID actorUserId, int entityType, UUID entityId) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        if (entityType <= 0 || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType/entityId 非法");
        }
    }

    public boolean resolveTargetState(boolean existed, Boolean requestedLiked) {
        return requestedLiked == null ? !existed : Boolean.TRUE.equals(requestedLiked);
    }

    public LikeChangedDomainEvent likeChangedEvent(
            UUID actorUserId,
            int entityType,
            UUID entityId,
            ResolvedSocialEntity resolved,
            boolean liked,
            Instant createTime
    ) {
        return new LikeChangedDomainEvent(
                actorUserId,
                entityType,
                entityId,
                resolved == null ? null : resolved.entityUserId(),
                resolved == null ? null : resolved.postId(),
                liked,
                createTime
        );
    }
}
