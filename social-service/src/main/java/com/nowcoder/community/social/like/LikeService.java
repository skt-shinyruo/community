package com.nowcoder.community.social.like;

import com.nowcoder.community.common.event.payload.LikePayload;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.event.SocialEventPublisher;
import com.nowcoder.community.social.like.dto.LikeRequest;
import com.nowcoder.community.social.like.dto.LikeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class LikeService {

    private final LikeRepository likeRepository;
    private final SocialEventPublisher eventPublisher;

    public LikeService(LikeRepository likeRepository, SocialEventPublisher eventPublisher) {
        this.likeRepository = likeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public LikeResponse setLike(int actorUserId, LikeRequest request) {
        if (actorUserId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        int entityType = request.getEntityType();
        int entityId = request.getEntityId();
        if (entityType <= 0 || entityId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType/entityId 非法");
        }

        boolean liked;
        if (request.getLiked() == null) {
            // 兼容 toggle 语义：前端不传 liked 时，按当前状态翻转
            liked = !likeRepository.isLiked(actorUserId, entityType, entityId);
        } else {
            // 兼容 set 语义：liked=true/false 代表目标状态（幂等）
            liked = Boolean.TRUE.equals(request.getLiked());
        }
        Integer entityUserId = request.getEntityUserId();

        if (liked) {
            boolean added = likeRepository.addLike(actorUserId, entityType, entityId);
            if (added) {
                if (entityUserId != null && entityUserId > 0) {
                    likeRepository.incrementUserLikeCount(entityUserId, 1);
                }
                LikePayload payload = new LikePayload();
                payload.setActorUserId(actorUserId);
                payload.setEntityType(entityType);
                payload.setEntityId(entityId);
                payload.setEntityUserId(entityUserId);
                payload.setPostId(request.getPostId());
                payload.setCreateTime(Instant.now());
                eventPublisher.publishLikeCreated(payload);
            }
        } else {
            boolean removed = likeRepository.removeLike(actorUserId, entityType, entityId);
            if (removed) {
                if (entityUserId != null && entityUserId > 0) {
                    likeRepository.incrementUserLikeCount(entityUserId, -1);
                }
            }
        }

        LikeResponse resp = new LikeResponse();
        resp.setLiked(likeRepository.isLiked(actorUserId, entityType, entityId));
        resp.setLikeCount(likeRepository.countEntityLikes(entityType, entityId));
        return resp;
    }

    public boolean isLiked(int actorUserId, int entityType, int entityId) {
        if (actorUserId <= 0 || entityType <= 0 || entityId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        return likeRepository.isLiked(actorUserId, entityType, entityId);
    }

    public long count(int entityType, int entityId) {
        if (entityType <= 0 || entityId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        return likeRepository.countEntityLikes(entityType, entityId);
    }

    public long userLikeCount(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return likeRepository.getUserLikeCount(userId);
    }

    public Map<Integer, Long> counts(int entityType, List<Integer> entityIds) {
        if (entityType <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        return likeRepository.countEntityLikesBatch(entityType, entityIds);
    }

    public Map<Integer, Boolean> statuses(int actorUserId, int entityType, List<Integer> entityIds) {
        if (actorUserId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        if (entityType <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        return likeRepository.likedStatusesBatch(actorUserId, entityType, entityIds);
    }
}
