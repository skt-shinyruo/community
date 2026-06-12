package com.nowcoder.community.social.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.social.application.command.SetLikeCommand;
import com.nowcoder.community.social.application.result.LikeResult;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import com.nowcoder.community.social.domain.model.LikeRelation;
import com.nowcoder.community.social.domain.model.ResolvedSocialEntity;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.domain.repository.LikeRepository;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import com.nowcoder.community.social.domain.service.LikeDomainService;
import com.nowcoder.community.user.api.action.UserRewardActionApi;
import com.nowcoder.community.user.api.model.UserLikeRewardRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.COMMENT;
import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service("socialLikeApplicationService")
public class LikeApplicationService {

    private static final Logger log = LoggerFactory.getLogger(LikeApplicationService.class);
    private static final int MAX_BATCH_ENTITY_IDS = 200;

    private final LikeRepository likeRepository;
    private final BlockRepository blockRepository;
    private final LikeDomainService likeDomainService;
    private final BlockDomainService blockDomainService;
    private final ContentEntityResolver contentEntityResolver;
    private final SocialDomainEventPublisher eventPublisher;
    private final UserRewardActionApi rewardActionApi;

    public LikeApplicationService(
            LikeRepository likeRepository,
            BlockRepository blockRepository,
            LikeDomainService likeDomainService,
            BlockDomainService blockDomainService,
            ContentEntityResolver contentEntityResolver,
            SocialDomainEventPublisher eventPublisher,
            UserRewardActionApi rewardActionApi
    ) {
        this.likeRepository = likeRepository;
        this.blockRepository = blockRepository;
        this.likeDomainService = likeDomainService;
        this.blockDomainService = blockDomainService;
        this.contentEntityResolver = contentEntityResolver;
        this.eventPublisher = eventPublisher;
        this.rewardActionApi = rewardActionApi;
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

        ResolvedSocialEntity resolved = resolvedForCreate == null
                ? resolveEntityForExistingRelation(actorUserId, entityType, entityId, liked)
                : resolvedForCreate;
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
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        validateLikeEntity(entityType, entityId);
        return likeRepository.isLiked(actorUserId, entityType, entityId);
    }

    public long count(int entityType, UUID entityId) {
        validateLikeEntity(entityType, entityId);
        return likeRepository.countEntityLikes(entityType, entityId);
    }

    @Transactional
    public long cleanupEntityLikes(int entityType, UUID entityId) {
        validateLikeEntity(entityType, entityId);
        return likeRepository.deleteLikesByEntity(entityType, entityId);
    }

    public Map<UUID, Long> counts(int entityType, List<UUID> entityIds) {
        validateLikeEntityType(entityType);
        List<UUID> ids = normalizeBatchEntityIds(entityIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return likeRepository.countEntityLikesBatch(entityType, ids);
    }

    public Map<UUID, Boolean> statuses(UUID actorUserId, int entityType, List<UUID> entityIds) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        validateLikeEntityType(entityType);
        List<UUID> ids = normalizeBatchEntityIds(entityIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return likeRepository.likedStatusesBatch(actorUserId, entityType, ids);
    }

    public long userLikeCount(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return likeRepository.getUserLikeCount(userId);
    }

    private void validateLikeEntity(int entityType, UUID entityId) {
        validateLikeEntityType(entityType);
        if (entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "entityId 非法");
        }
    }

    private void validateLikeEntityType(int entityType) {
        if (entityType != USER && entityType != POST && entityType != COMMENT) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
    }

    private List<UUID> normalizeBatchEntityIds(List<UUID> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return List.of();
        }
        if (entityIds.size() > MAX_BATCH_ENTITY_IDS) {
            throw new BusinessException(INVALID_ARGUMENT, "entityIds 不能超过200");
        }
        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>();
        for (UUID entityId : entityIds) {
            if (entityId == null) {
                throw new BusinessException(INVALID_ARGUMENT, "entityIds 非法");
            }
            uniqueIds.add(entityId);
        }
        return new ArrayList<>(uniqueIds);
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
            if (rewardActionApi != null) {
                sideEffectEventId = ensureSideEffectEventId(sideEffectEventId, event, "like-created-reward", false);
                rewardActionApi.awardLikeCreated(new UserLikeRewardRequest(
                        sideEffectEventId,
                        event.actorUserId(),
                        event.entityUserId()
                ));
            }
            return;
        }
        if (rewardActionApi != null) {
            sideEffectEventId = ensureSideEffectEventId(sideEffectEventId, event, "like-removed-reward", false);
            rewardActionApi.awardLikeRemoved(new UserLikeRewardRequest(
                    sideEffectEventId,
                    event.actorUserId(),
                    event.entityUserId()
            ));
        }
    }

    private ResolvedSocialEntity resolveEntityForExistingRelation(UUID actorUserId, int entityType, UUID entityId, boolean liked) {
        try {
            return resolveEntity(entityType, entityId);
        } catch (BusinessException ex) {
            if (!liked && isContentNotFound(ex.getErrorCode())) {
                UUID storedOwnerId = likeRepository.findLike(actorUserId, entityType, entityId)
                        .map(LikeRelation::entityUserId)
                        .orElse(null);
                return new ResolvedSocialEntity(storedOwnerId, null);
            }
            throw ex;
        }
    }

    private boolean isContentNotFound(ErrorCode errorCode) {
        return errorCode == NOT_FOUND || (errorCode != null && errorCode.getHttpStatus() == 404);
    }

    private String ensureSideEffectEventId(String currentEventId, LikeChangedDomainEvent event, String prefix, boolean deterministic) {
        if (currentEventId != null) {
            return currentEventId;
        }
        if (!deterministic) {
            return prefix + ":" + UUID.randomUUID();
        }
        return prefix + ":" + event.actorUserId() + ":" + event.entityType() + ":" + event.entityId();
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
