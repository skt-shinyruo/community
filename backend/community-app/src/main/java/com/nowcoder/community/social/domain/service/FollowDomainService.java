package com.nowcoder.community.social.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.domain.event.FollowCreatedDomainEvent;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.social.exception.SocialErrorCode.CANNOT_FOLLOW_SELF;

public class FollowDomainService {

    public void validateFollow(UUID actorUserId, int entityType, UUID entityId) {
        validateUserRelation(actorUserId, entityType, entityId, "follow 仅支持 USER");
        if (actorUserId.equals(entityId)) {
            throw new BusinessException(CANNOT_FOLLOW_SELF);
        }
    }

    public void validateUnfollow(UUID actorUserId, int entityType, UUID entityId) {
        validateUserRelation(actorUserId, entityType, entityId, "unfollow 仅支持 USER");
    }

    public FollowCreatedDomainEvent followCreatedEvent(
            UUID actorUserId,
            int entityType,
            UUID entityId,
            Instant createTime
    ) {
        return new FollowCreatedDomainEvent(actorUserId, entityType, entityId, entityId, createTime);
    }

    private void validateUserRelation(UUID actorUserId, int entityType, UUID entityId, String unsupportedMessage) {
        if (actorUserId == null || entityType <= 0 || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        if (entityType != USER) {
            throw new BusinessException(INVALID_ARGUMENT, unsupportedMessage);
        }
    }
}
