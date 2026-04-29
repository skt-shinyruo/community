package com.nowcoder.community.social.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.growth.api.action.GrowthTaskProgressActionApi;
import com.nowcoder.community.growth.api.model.GrowthLikeTaskProgressRequest;
import com.nowcoder.community.social.application.command.SetLikeCommand;
import com.nowcoder.community.social.application.result.LikeResult;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import com.nowcoder.community.social.domain.model.ResolvedSocialEntity;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.domain.repository.LikeRepository;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import com.nowcoder.community.social.domain.service.LikeDomainService;
import com.nowcoder.community.user.api.action.UserPointsAwardActionApi;
import com.nowcoder.community.user.api.model.UserLikePointsAwardRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Service("socialLikeApplicationService")
public class LikeApplicationService {

    private static final Logger log = LoggerFactory.getLogger(LikeApplicationService.class);

    private final LikeRepository likeRepository;
    private final BlockRepository blockRepository;
    private final LikeDomainService likeDomainService;
    private final BlockDomainService blockDomainService;
    private final ContentEntityResolver contentEntityResolver;
    private final SocialDomainEventPublisher eventPublisher;
    private final UserPointsAwardActionApi pointsAwardActionApi;
    private final GrowthTaskProgressActionApi taskProgressActionApi;

    public LikeApplicationService(
            LikeRepository likeRepository,
            BlockRepository blockRepository,
            LikeDomainService likeDomainService,
            BlockDomainService blockDomainService,
            ContentEntityResolver contentEntityResolver,
            SocialDomainEventPublisher eventPublisher,
            UserPointsAwardActionApi pointsAwardActionApi,
            GrowthTaskProgressActionApi taskProgressActionApi
    ) {
        this.likeRepository = likeRepository;
        this.blockRepository = blockRepository;
        this.likeDomainService = likeDomainService;
        this.blockDomainService = blockDomainService;
        this.contentEntityResolver = contentEntityResolver;
        this.eventPublisher = eventPublisher;
        this.pointsAwardActionApi = pointsAwardActionApi;
        this.taskProgressActionApi = taskProgressActionApi;
    }

    @Transactional
    public LikeResult setLike(SetLikeCommand command) {
        UUID actorUserId = command.actorUserId();
        int entityType = command.entityType();
        UUID entityId = command.entityId();
        likeDomainService.validateLike(actorUserId, entityType, entityId);

        boolean existed = likeRepository.isLiked(actorUserId, entityType, entityId);
        boolean liked = likeDomainService.resolveTargetState(existed, command.liked());
        if (liked == existed) {
            return buildResult(actorUserId, entityType, entityId);
        }

        ResolvedSocialEntity resolvedForCreate = null;
        if (liked && !existed) {
            resolvedForCreate = resolveEntity(entityType, entityId);
            if (resolvedForCreate.entityUserId() != null
                    && blockDomainService.isEitherBlocked(actorUserId, resolvedForCreate.entityUserId(), blockRepository)) {
                throw new BusinessException(FORBIDDEN, "双方存在拉黑关系，无法执行该操作");
            }
        }

        ResolvedSocialEntity resolved = resolvedForCreate == null ? resolveEntity(entityType, entityId) : resolvedForCreate;
        boolean changed = likeRepository.setLike(actorUserId, entityType, entityId, resolved.entityUserId(), liked);
        if (!changed) {
            return buildResult(actorUserId, entityType, entityId);
        }

        Runnable rollback = () -> likeRepository.setLike(actorUserId, entityType, entityId, resolved.entityUserId(), !liked);
        boolean needsExplicitCompensation = likeRepository.requiresExplicitCompensation();
        if (needsExplicitCompensation) {
            registerRollbackIfTxRolledBack("like", rollback);
        }

        Instant createTime = Instant.now();
        LikeChangedDomainEvent event = likeDomainService.likeChangedEvent(
                actorUserId,
                entityType,
                entityId,
                resolved,
                liked,
                createTime
        );
        try {
            publishSideEffects(event);
            eventPublisher.publishLikeChanged(event);
        } catch (RuntimeException ex) {
            if (needsExplicitCompensation) {
                compensate("like", rollback);
            }
            throw ex;
        }
        return buildResult(actorUserId, entityType, entityId);
    }

    public boolean isLiked(UUID actorUserId, int entityType, UUID entityId) {
        if (actorUserId == null || entityType <= 0 || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        return likeRepository.isLiked(actorUserId, entityType, entityId);
    }

    public long count(int entityType, UUID entityId) {
        if (entityType <= 0 || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        return likeRepository.countEntityLikes(entityType, entityId);
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

    public long userLikeCount(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return likeRepository.getUserLikeCount(userId);
    }

    private ResolvedSocialEntity resolveEntity(int entityType, UUID entityId) {
        if (entityType == USER) {
            return new ResolvedSocialEntity(entityId, null);
        }
        if (entityType == POST || entityType == COMMENT) {
            ContentEntityResolver.ResolvedEntity resolved = contentEntityResolver.resolve(entityType, entityId);
            return new ResolvedSocialEntity(resolved.getEntityUserId(), resolved.getPostId());
        }
        throw new BusinessException(INVALID_ARGUMENT, "entityType 不支持");
    }

    private void publishSideEffects(LikeChangedDomainEvent event) {
        String sideEffectEventId = null;
        if (event.liked()) {
            if (pointsAwardActionApi != null) {
                sideEffectEventId = ensureSideEffectEventId(sideEffectEventId, "like-created");
                pointsAwardActionApi.awardLikeCreated(new UserLikePointsAwardRequest(
                        sideEffectEventId,
                        event.actorUserId(),
                        event.entityUserId()
                ));
            }
            if (taskProgressActionApi != null) {
                sideEffectEventId = ensureSideEffectEventId(sideEffectEventId, "like-created");
                taskProgressActionApi.triggerLikeCreated(new GrowthLikeTaskProgressRequest(
                        sideEffectEventId,
                        event.actorUserId(),
                        event.entityUserId(),
                        event.createTime()
                ));
            }
            return;
        }
        if (pointsAwardActionApi != null) {
            sideEffectEventId = ensureSideEffectEventId(sideEffectEventId, "like-removed");
            pointsAwardActionApi.awardLikeRemoved(new UserLikePointsAwardRequest(
                    sideEffectEventId,
                    event.actorUserId(),
                    event.entityUserId()
            ));
        }
    }

    private String ensureSideEffectEventId(String currentEventId, String prefix) {
        return currentEventId == null ? prefix + ":" + UUID.randomUUID() : currentEventId;
    }

    private LikeResult buildResult(UUID actorUserId, int entityType, UUID entityId) {
        return new LikeResult(
                likeRepository.isLiked(actorUserId, entityType, entityId),
                likeRepository.countEntityLikes(entityType, entityId)
        );
    }

    private void registerRollbackIfTxRolledBack(String name, Runnable rollback) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                compensate(name, rollback);
            }
        });
    }

    private void compensate(String name, Runnable rollback) {
        try {
            rollback.run();
        } catch (RuntimeException ex) {
            log.warn("[{}] rollback failed: {}", name, ex.toString());
        }
    }
}
