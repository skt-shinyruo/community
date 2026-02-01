package com.nowcoder.community.social.follow;

import com.nowcoder.community.common.event.payload.FollowPayload;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.event.SocialEventPublisher;
import com.nowcoder.community.social.follow.dto.FollowItem;
import com.nowcoder.community.social.follow.dto.FollowRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static com.nowcoder.community.common.domain.EntityTypes.USER;
import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final SocialEventPublisher eventPublisher;

    public FollowService(FollowRepository followRepository, SocialEventPublisher eventPublisher) {
        this.followRepository = followRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void follow(int actorUserId, FollowRequest request) {
        if (actorUserId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        int entityType = request.getEntityType();
        int entityId = request.getEntityId();
        if (entityType <= 0 || entityId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType/entityId 非法");
        }
        if (entityType != USER) {
            throw new BusinessException(INVALID_ARGUMENT, "follow 仅支持 USER");
        }
        if (actorUserId == entityId) {
            throw new BusinessException(INVALID_ARGUMENT, "不能关注自己");
        }

        long now = System.currentTimeMillis();
        boolean created = followRepository.follow(actorUserId, entityType, entityId, now);
        if (created) {
            FollowPayload payload = new FollowPayload();
            payload.setActorUserId(actorUserId);
            payload.setEntityType(entityType);
            payload.setEntityId(entityId);
            payload.setEntityUserId(entityId);
            payload.setCreateTime(Instant.ofEpochMilli(now));
            eventPublisher.publishFollowCreated(payload);
        }
    }

    @Transactional
    public void unfollow(int actorUserId, int entityType, int entityId) {
        if (actorUserId <= 0 || entityType <= 0 || entityId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        if (entityType != USER) {
            throw new BusinessException(INVALID_ARGUMENT, "unfollow 仅支持 USER");
        }
        followRepository.unfollow(actorUserId, entityType, entityId);
    }

    public boolean hasFollowed(int actorUserId, int entityType, int entityId) {
        if (actorUserId <= 0 || entityType <= 0 || entityId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        if (entityType != USER) {
            return false;
        }
        return followRepository.hasFollowed(actorUserId, entityType, entityId);
    }

    public long followeeCount(int userId, int entityType) {
        if (userId <= 0 || entityType <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        if (entityType != USER) {
            return 0;
        }
        return followRepository.countFollowees(userId, entityType);
    }

    public long followerCount(int entityType, int entityId) {
        if (entityType <= 0 || entityId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        if (entityType != USER) {
            return 0;
        }
        return followRepository.countFollowers(entityType, entityId);
    }

    public List<FollowItem> listFollowees(int userId, int entityType, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        if (entityType != USER) {
            return List.of();
        }
        return followRepository.listFollowees(userId, entityType, p * s, s);
    }

    public List<FollowItem> listFollowers(int entityType, int entityId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        if (entityType != USER) {
            return List.of();
        }
        return followRepository.listFollowers(entityType, entityId, p * s, s);
    }
}
