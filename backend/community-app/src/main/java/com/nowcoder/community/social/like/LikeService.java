package com.nowcoder.community.social.like;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.api.query.SocialLikeQueryApi;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.block.BlockService;
import com.nowcoder.community.social.event.SocialEventPublisher;
import com.nowcoder.community.social.like.dto.LikeRequest;
import com.nowcoder.community.social.like.dto.LikeResponse;
import com.nowcoder.community.social.service.ContentEntityResolver;
import com.nowcoder.community.growth.service.TaskProgressTriggerService;
import com.nowcoder.community.user.service.PointsAwardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.COMMENT;
import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class LikeService implements SocialLikeQueryApi {

    private static final Logger log = LoggerFactory.getLogger(LikeService.class);

    private final LikeRepository likeRepository;
    private final SocialEventPublisher eventPublisher;
    private final ContentEntityResolver contentEntityResolver;
    private final BlockService blockService;
    private final PointsAwardService pointsAwardService;
    private final TaskProgressTriggerService taskProgressTriggerService;

    @Autowired
    public LikeService(
            LikeRepository likeRepository,
            SocialEventPublisher eventPublisher,
            ContentEntityResolver contentEntityResolver,
            BlockService blockService,
            PointsAwardService pointsAwardService,
            TaskProgressTriggerService taskProgressTriggerService
    ) {
        this.likeRepository = likeRepository;
        this.eventPublisher = eventPublisher;
        this.contentEntityResolver = contentEntityResolver;
        this.blockService = blockService;
        this.pointsAwardService = pointsAwardService;
        this.taskProgressTriggerService = taskProgressTriggerService;
    }

    @Transactional
    public LikeResponse setLike(UUID actorUserId, LikeRequest request) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        int entityType = request.getEntityType();
        UUID entityId = request.getEntityId();
        if (entityType <= 0 || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType/entityId 非法");
        }

        boolean existed = likeRepository.isLiked(actorUserId, entityType, entityId);
        // 兼容 toggle / set 两种语义：
        // - toggle：不传 liked 时翻转当前状态
        // - set：liked=true/false 代表目标状态（幂等）
        boolean liked = (request.getLiked() == null) ? !existed : Boolean.TRUE.equals(request.getLiked());
        if (liked == existed) {
            return buildResponse(actorUserId, entityType, entityId);
        }

        // 反骚扰：仅阻断“创建点赞”副作用（like existed=false -> liked=true）。
        // 允许取消点赞（清理自身状态），也允许幂等重复 set=true（不产生新副作用）。
        ResolvedEntity resolvedForCreate = null;
        if (liked && !existed) {
            resolvedForCreate = resolveEntityForPayload(entityType, entityId);
            if (resolvedForCreate.entityUserId != null
                    && blockService != null
                    && blockService.isEitherBlocked(actorUserId, resolvedForCreate.entityUserId)) {
                throw new BusinessException(FORBIDDEN, "双方存在拉黑关系，无法执行该操作");
            }
        }

        ResolvedEntity resolved = resolvedForCreate == null ? resolveEntityForPayload(entityType, entityId) : resolvedForCreate;
        UUID entityUserId = resolved.entityUserId;

        boolean changed = likeRepository.setLike(actorUserId, entityType, entityId, entityUserId, liked);
        if (!changed) {
            return buildResponse(actorUserId, entityType, entityId);
        }

        Runnable rollback = () -> likeRepository.setLike(actorUserId, entityType, entityId, entityUserId, !liked);
        boolean needsExplicitCompensation = likeRepository.requiresExplicitCompensation();
        if (needsExplicitCompensation) {
            registerRollbackIfTxRolledBack(rollback);
        }

        LikePayload payload = new LikePayload();
        payload.setActorUserId(actorUserId);
        payload.setEntityType(entityType);
        payload.setEntityId(entityId);
        payload.setEntityUserId(entityUserId);
        payload.setPostId(resolved.postId);
        payload.setCreateTime(Instant.now());
        String sideEffectEventId = null;
        try {
            if (liked) {
                if (pointsAwardService != null) {
                    sideEffectEventId = ensureSideEffectEventId(sideEffectEventId, "like-created");
                    pointsAwardService.awardLikeCreated(sideEffectEventId, payload);
                }
                if (taskProgressTriggerService != null) {
                    sideEffectEventId = ensureSideEffectEventId(sideEffectEventId, "like-created");
                    taskProgressTriggerService.triggerLikeCreated(sideEffectEventId, payload);
                }
                eventPublisher.publishLikeCreated(payload);
            } else {
                if (pointsAwardService != null) {
                    sideEffectEventId = ensureSideEffectEventId(sideEffectEventId, "like-removed");
                    pointsAwardService.awardLikeRemoved(sideEffectEventId, payload);
                }
                eventPublisher.publishLikeRemoved(payload);
            }
        } catch (RuntimeException ex) {
            if (needsExplicitCompensation) {
                try {
                    rollback.run();
                } catch (RuntimeException rollbackEx) {
                    log.warn("[like] rollback failed after publish error (entityType={}, entityId={}, actorUserId={}, liked={}): {}",
                            entityType, entityId, actorUserId, liked, rollbackEx.toString());
                }
            }
            throw ex;
        }
        return buildResponse(actorUserId, entityType, entityId);
    }

    private String ensureSideEffectEventId(String currentEventId, String prefix) {
        if (currentEventId != null) {
            return currentEventId;
        }
        return prefix + ":" + UUID.randomUUID();
    }

    private void registerRollbackIfTxRolledBack(Runnable rollback) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    rollback.run();
                } catch (RuntimeException ex) {
                    log.warn("[like] rollback failed after tx rollback: {}", ex.toString());
                }
            }
        });
    }

    private ResolvedEntity resolveEntityForPayload(int entityType, UUID entityId) {
        if (entityType == USER) {
            // user 类型的 like（如未来启用）：由 entityId 自洽推导，避免信任客户端注入字段。
            ResolvedEntity r = new ResolvedEntity();
            r.entityUserId = entityId;
            return r;
        }
        if (entityType == POST || entityType == COMMENT) {
            ContentEntityResolver.ResolvedEntity resolved = contentEntityResolver.resolve(entityType, entityId);
            ResolvedEntity r = new ResolvedEntity();
            r.entityUserId = resolved.getEntityUserId();
            r.postId = resolved.getPostId();
            return r;
        }
        throw new BusinessException(INVALID_ARGUMENT, "entityType 不支持");
    }

    private static class ResolvedEntity {
        private UUID entityUserId;
        private UUID postId;
    }

    private LikeResponse buildResponse(UUID actorUserId, int entityType, UUID entityId) {
        LikeResponse resp = new LikeResponse();
        resp.setLiked(likeRepository.isLiked(actorUserId, entityType, entityId));
        resp.setLikeCount(likeRepository.countEntityLikes(entityType, entityId));
        return resp;
    }

    @Override
    public boolean isLiked(UUID actorUserId, int entityType, UUID entityId) {
        if (actorUserId == null || entityType <= 0 || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        return likeRepository.isLiked(actorUserId, entityType, entityId);
    }

    @Override
    public long count(int entityType, UUID entityId) {
        if (entityType <= 0 || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        return likeRepository.countEntityLikes(entityType, entityId);
    }

    @Override
    public long userLikeCount(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return likeRepository.getUserLikeCount(userId);
    }

    public Map<UUID, Long> counts(int entityType, List<UUID> entityIds) {
        if (entityType <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        return likeRepository.countEntityLikesBatch(entityType, entityIds);
    }

    public Map<UUID, Boolean> statuses(UUID actorUserId, int entityType, List<UUID> entityIds) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        if (entityType <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        return likeRepository.likedStatusesBatch(actorUserId, entityType, entityIds);
    }
}
