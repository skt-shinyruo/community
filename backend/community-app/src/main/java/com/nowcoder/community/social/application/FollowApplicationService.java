package com.nowcoder.community.social.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.infra.pagination.Pagination;
import com.nowcoder.community.social.application.command.FollowCommand;
import com.nowcoder.community.social.application.command.UnfollowCommand;
import com.nowcoder.community.social.application.result.FollowRelationResult;
import com.nowcoder.community.social.domain.event.FollowCreatedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import com.nowcoder.community.social.domain.model.FollowRelation;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.domain.repository.FollowRepository;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import com.nowcoder.community.social.domain.service.FollowDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service("socialFollowApplicationService")
public class FollowApplicationService {

    private static final Logger log = LoggerFactory.getLogger(FollowApplicationService.class);

    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final FollowDomainService followDomainService;
    private final BlockDomainService blockDomainService;
    private final SocialDomainEventPublisher eventPublisher;

    public FollowApplicationService(
            FollowRepository followRepository,
            BlockRepository blockRepository,
            FollowDomainService followDomainService,
            BlockDomainService blockDomainService,
            SocialDomainEventPublisher eventPublisher
    ) {
        this.followRepository = followRepository;
        this.blockRepository = blockRepository;
        this.followDomainService = followDomainService;
        this.blockDomainService = blockDomainService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void follow(FollowCommand command) {
        UUID actorUserId = command.actorUserId();
        int entityType = command.entityType();
        UUID entityId = command.entityId();
        followDomainService.validateFollow(actorUserId, entityType, entityId);

        boolean existed = followRepository.hasFollowed(actorUserId, entityType, entityId);
        if (!existed && blockDomainService.isEitherBlocked(actorUserId, entityId, blockRepository)) {
            throw new BusinessException(FORBIDDEN, "双方存在拉黑关系，无法执行该操作");
        }

        long now = System.currentTimeMillis();
        boolean created = followRepository.follow(actorUserId, entityType, entityId, now);
        if (!created) {
            return;
        }

        Runnable rollback = () -> followRepository.unfollow(actorUserId, entityType, entityId);
        boolean needsExplicitCompensation = followRepository.requiresExplicitCompensation();
        if (needsExplicitCompensation) {
            registerRollbackIfTxRolledBack(rollback);
        }
        FollowCreatedDomainEvent event = followDomainService.followCreatedEvent(
                actorUserId,
                entityType,
                entityId,
                Instant.ofEpochMilli(now)
        );
        try {
            eventPublisher.publishFollowCreated(event);
        } catch (RuntimeException ex) {
            if (needsExplicitCompensation) {
                compensate(rollback);
            }
            throw ex;
        }
    }

    @Transactional
    public void unfollow(UnfollowCommand command) {
        followDomainService.validateUnfollow(command.actorUserId(), command.entityType(), command.entityId());
        followRepository.unfollow(command.actorUserId(), command.entityType(), command.entityId());
    }

    public boolean hasFollowed(UUID actorUserId, int entityType, UUID entityId) {
        validateFollowRelationQuery(actorUserId, entityType, entityId);
        return followRepository.hasFollowed(actorUserId, entityType, entityId);
    }

    public long followeeCount(UUID userId, int entityType) {
        validateFollowUserQuery(userId, entityType);
        return followRepository.countFolloweesExcludingBlocked(userId, entityType, blockRepository);
    }

    public long followerCount(int entityType, UUID entityId) {
        validateFollowTargetQuery(entityType, entityId);
        return followRepository.countFollowersExcludingBlocked(entityType, entityId, blockRepository);
    }

    public List<FollowRelationResult> listFollowees(UUID userId, int entityType, int page, int size) {
        validateFollowUserQuery(userId, entityType);
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return followRepository.listFolloweesExcludingBlocked(userId, entityType, blockRepository, Pagination.safeOffset(p, s), s)
                .stream()
                .map(this::toResult)
                .toList();
    }

    public List<FollowRelationResult> listFollowers(int entityType, UUID entityId, int page, int size) {
        validateFollowTargetQuery(entityType, entityId);
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return followRepository.listFollowersExcludingBlocked(entityType, entityId, blockRepository, Pagination.safeOffset(p, s), s)
                .stream()
                .map(this::toResult)
                .toList();
    }

    private void validateFollowRelationQuery(UUID actorUserId, int entityType, UUID entityId) {
        if (actorUserId == null || entityType <= 0 || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        validateUserOnlyEntityType(entityType);
    }

    private void validateFollowUserQuery(UUID userId, int entityType) {
        if (userId == null || entityType <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        validateUserOnlyEntityType(entityType);
    }

    private void validateFollowTargetQuery(int entityType, UUID entityId) {
        if (entityType <= 0 || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        validateUserOnlyEntityType(entityType);
    }

    private void validateUserOnlyEntityType(int entityType) {
        if (entityType != USER) {
            throw new BusinessException(INVALID_ARGUMENT, "follow 仅支持 USER");
        }
    }

    private FollowRelationResult toResult(FollowRelation relation) {
        return new FollowRelationResult(relation.targetId(), relation.followTime());
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
                compensate(rollback);
            }
        });
    }

    private void compensate(Runnable rollback) {
        try {
            rollback.run();
        } catch (RuntimeException ex) {
            log.warn("[follow] rollback failed: {}", ex.toString());
        }
    }
}
