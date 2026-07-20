package com.nowcoder.community.social.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.social.application.command.CleanupDeletedContentLikesCommand;
import com.nowcoder.community.social.application.command.SetLikeCommand;
import com.nowcoder.community.social.application.result.LikeResult;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import com.nowcoder.community.social.domain.model.LikeRelation;
import com.nowcoder.community.social.domain.model.LikeTargetState;
import com.nowcoder.community.social.domain.model.ResolvedSocialEntity;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.domain.repository.LikeRepository;
import com.nowcoder.community.social.domain.repository.LikeTargetStateRepository;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import com.nowcoder.community.social.domain.service.LikeDomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.COMMENT;
import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service("socialLikeApplicationService")
public class LikeApplicationService {

    private static final int MAX_BATCH_ENTITY_IDS = 200;
    private static final int CLEANUP_SCAN_LIMIT = 200;

    private final LikeRepository likeRepository;
    private final BlockRepository blockRepository;
    private final LikeDomainService likeDomainService;
    private final BlockDomainService blockDomainService;
    private final SocialDomainEventPublisher eventPublisher;
    private final LikeTargetStateRepository targetStateRepository;
    private final LikeCleanupMetrics cleanupMetrics;
    private final UuidV7Generator idGenerator;

    @Autowired
    public LikeApplicationService(
            LikeRepository likeRepository,
            BlockRepository blockRepository,
            LikeDomainService likeDomainService,
            BlockDomainService blockDomainService,
            SocialDomainEventPublisher eventPublisher,
            LikeTargetStateRepository targetStateRepository,
            LikeCleanupMetrics cleanupMetrics,
            UuidV7Generator idGenerator
    ) {
        this.likeRepository = likeRepository;
        this.blockRepository = blockRepository;
        this.likeDomainService = likeDomainService;
        this.blockDomainService = blockDomainService;
        this.eventPublisher = eventPublisher;
        this.targetStateRepository = targetStateRepository;
        this.cleanupMetrics = cleanupMetrics;
        this.idGenerator = idGenerator;
    }

    public LikeApplicationService(
            LikeRepository likeRepository,
            BlockRepository blockRepository,
            LikeDomainService likeDomainService,
            BlockDomainService blockDomainService,
            SocialDomainEventPublisher eventPublisher,
            LikeTargetStateRepository targetStateRepository,
            UuidV7Generator idGenerator
    ) {
        this(
                likeRepository,
                blockRepository,
                likeDomainService,
                blockDomainService,
                eventPublisher,
                targetStateRepository,
                LikeCleanupMetrics.noop(),
                idGenerator
        );
    }

    @Transactional
    public LikeResult setLike(SetLikeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID actorUserId = command.actorUserId();
        int entityType = command.entityType();
        UUID entityId = command.entityId();
        likeDomainService.validateLike(actorUserId, entityType, entityId);
        ResolvedSocialEntity resolved = requireResolvedTarget(command);
        LikeTargetState targetState = lockContentTarget(entityType, entityId);
        if (command.liked() && targetState != null && targetState.isDeleted()) {
            throw new BusinessException(NOT_FOUND, "like target has been deleted");
        }

        Optional<LikeRelation> existingRelation = likeRepository.findLike(actorUserId, entityType, entityId);
        boolean existed = existingRelation.isPresent();
        boolean liked = likeDomainService.resolveTargetState(existed, command.liked());
        if (liked == existed) {
            return buildResult(actorUserId, entityType, entityId);
        }

        if (liked && !existed) {
            if (blockDomainService.isEitherBlocked(actorUserId, resolved.entityUserId(), blockRepository)) {
                throw new BusinessException(FORBIDDEN, "双方存在拉黑关系，无法执行该操作");
            }
        }

        LikeRelation changedRelation;
        boolean changed;
        if (liked) {
            LikeRelation candidate = new LikeRelation(
                    idGenerator.next(),
                    actorUserId,
                    entityType,
                    entityId,
                    resolved.entityUserId()
            );
            changed = likeRepository.addLike(candidate);
            changedRelation = candidate;
        } else {
            LikeRelation expectedRelation = existingRelation.orElseThrow();
            changed = likeRepository.removeLike(expectedRelation);
            changedRelation = expectedRelation;
        }
        if (!changed) {
            return buildResult(actorUserId, entityType, entityId);
        }
        if (changedRelation.entityUserId() != null) {
            likeRepository.incrementUserLikeCount(changedRelation.entityUserId(), liked ? 1L : -1L);
        }

        Instant occurredAt = Instant.now();
        LikeChangedDomainEvent event = likeDomainService.likeChangedEvent(
                changedRelation,
                resolved,
                liked,
                occurredAt
        );
        eventPublisher.publishLikeChanged(event);
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
    public long cleanupDeletedContentLikes(CleanupDeletedContentLikesCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if ((command.entityType() != POST && command.entityType() != COMMENT)
                || command.entityId() == null
                || command.sourceEventId() == null
                || command.sourceEventId().isBlank()
                || command.sourceVersion() <= 0L
                || command.deletedAt() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "deleted content cleanup command 非法");
        }
        String source = cleanupSource(command.sourceEventId());
        try {
            long removed = cleanupDeletedContentLikesCore(command);
            cleanupMetrics.recordCleanup(source, "success");
            cleanupMetrics.recordCleanupLag(Duration.between(command.deletedAt(), Instant.now()));
            return removed;
        } catch (RuntimeException exception) {
            cleanupMetrics.recordCleanup(source, "failed");
            throw exception;
        }
    }

    private long cleanupDeletedContentLikesCore(CleanupDeletedContentLikesCommand command) {
        targetStateRepository.insertActiveIfAbsent(command.entityType(), command.entityId());
        LikeTargetState current = targetStateRepository.findForUpdate(command.entityType(), command.entityId());
        LikeTargetState updated = current.applyDeletion(
                command.sourceEventId(),
                command.sourceVersion(),
                command.deletedAt()
        );
        if (updated == current) {
            return isReconciliation(command.sourceEventId())
                    ? cleanupEntityLikesCore(command.entityType(), command.entityId())
                    : 0L;
        }
        if (!targetStateRepository.saveIfNewer(updated)) {
            throw new IllegalStateException("failed to advance like target deletion fence");
        }
        return cleanupEntityLikesCore(command.entityType(), command.entityId());
    }

    private long cleanupEntityLikesCore(int entityType, UUID entityId) {
        long removed = 0L;
        UUID afterActorUserId = new UUID(0L, 0L);
        while (true) {
            List<LikeRelation> page = likeRepository.scanLikesByEntity(entityType, entityId, afterActorUserId, CLEANUP_SCAN_LIMIT);
            if (page == null || page.isEmpty()) {
                return removed;
            }
            for (LikeRelation relation : page) {
                boolean changed = likeRepository.removeLike(relation);
                afterActorUserId = relation.actorUserId();
                if (!changed) {
                    continue;
                }
                if (relation.entityUserId() != null) {
                    likeRepository.incrementUserLikeCount(relation.entityUserId(), -1L);
                }
                LikeChangedDomainEvent event = likeDomainService.likeChangedEvent(
                        relation,
                        new ResolvedSocialEntity(relation.entityUserId(), entityType == POST ? entityId : null),
                        false,
                        Instant.now()
                );
                eventPublisher.publishLikeChanged(event);
                removed++;
            }
        }
    }

    private LikeTargetState lockContentTarget(int entityType, UUID entityId) {
        if (entityType != POST && entityType != COMMENT) {
            return null;
        }
        targetStateRepository.insertActiveIfAbsent(entityType, entityId);
        return targetStateRepository.findForUpdate(entityType, entityId);
    }

    private String cleanupSource(String sourceEventId) {
        return isReconciliation(sourceEventId)
                ? "reconciliation"
                : "content_event";
    }

    private boolean isReconciliation(String sourceEventId) {
        return sourceEventId != null && sourceEventId.startsWith("social-like-reconciliation:");
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

    private ResolvedSocialEntity requireResolvedTarget(SetLikeCommand command) {
        UUID entityUserId = command.entityUserId();
        UUID postId = command.postId();
        if (command.entityType() == USER) {
            if (!command.entityId().equals(entityUserId) || postId != null) {
                throw new BusinessException(INVALID_ARGUMENT, "resolved user like target invalid");
            }
            return new ResolvedSocialEntity(entityUserId, null);
        }
        if (command.entityType() == POST) {
            if (entityUserId == null || !command.entityId().equals(postId)) {
                throw new BusinessException(INVALID_ARGUMENT, "resolved post like target invalid");
            }
            return new ResolvedSocialEntity(entityUserId, postId);
        }
        if (command.entityType() == COMMENT) {
            if (entityUserId == null || postId == null) {
                throw new BusinessException(INVALID_ARGUMENT, "resolved comment like target invalid");
            }
            return new ResolvedSocialEntity(entityUserId, postId);
        }
        throw new BusinessException(INVALID_ARGUMENT, "entityType 不支持");
    }

    private LikeResult buildResult(UUID actorUserId, int entityType, UUID entityId) {
        return new LikeResult(
                likeRepository.isLiked(actorUserId, entityType, entityId),
                likeRepository.countEntityLikes(entityType, entityId)
        );
    }

}
