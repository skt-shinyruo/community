package com.nowcoder.community.social.application;

import com.nowcoder.community.social.application.command.BlockCommand;
import com.nowcoder.community.social.application.command.UnblockCommand;
import com.nowcoder.community.social.application.result.BlockRelationResult;
import com.nowcoder.community.social.domain.event.BlockRelationChangedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import com.nowcoder.community.social.domain.model.BlockRelation;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.domain.repository.FollowRepository;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service("socialBlockApplicationService")
public class BlockApplicationService {

    private static final Logger log = LoggerFactory.getLogger(BlockApplicationService.class);

    private final BlockRepository blockRepository;
    private final FollowRepository followRepository;
    private final BlockDomainService blockDomainService;
    private final SocialDomainEventPublisher eventPublisher;

    public BlockApplicationService(
            BlockRepository blockRepository,
            FollowRepository followRepository,
            BlockDomainService blockDomainService,
            SocialDomainEventPublisher eventPublisher
    ) {
        this.blockRepository = blockRepository;
        this.followRepository = followRepository;
        this.blockDomainService = blockDomainService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void block(BlockCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        blockDomainService.validateBlock(command.actorUserId(), command.targetUserId());
        long version = blockRepository.nextBlockProjectionVersion();
        boolean changed = blockRepository.block(command.actorUserId(), command.targetUserId(), version);
        boolean removedForwardFollow = followRepository.unfollow(command.actorUserId(), USER, command.targetUserId());
        boolean removedReverseFollow = followRepository.unfollow(command.targetUserId(), USER, command.actorUserId());
        Runnable restoreRemovedFollows = () -> {
            long now = System.currentTimeMillis();
            if (removedForwardFollow) {
                followRepository.follow(command.actorUserId(), USER, command.targetUserId(), now);
            }
            if (removedReverseFollow) {
                followRepository.follow(command.targetUserId(), USER, command.actorUserId(), now);
            }
        };
        if (!changed) {
            if ((removedForwardFollow || removedReverseFollow) && followRepository.requiresExplicitCompensation()) {
                registerRollbackIfTxRolledBack(restoreRemovedFollows);
            }
            return;
        }
        Runnable rollback = () -> {
            blockRepository.unblock(command.actorUserId(), command.targetUserId());
            restoreRemovedFollows.run();
        };
        publishChangedWithCompensation(
                new BlockRelationChangedDomainEvent(command.actorUserId(), command.targetUserId(), true, version),
                rollback
        );
    }

    @Transactional
    public void unblock(UnblockCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        blockDomainService.validateUnblock(command.actorUserId(), command.targetUserId());
        long version = blockRepository.nextBlockProjectionVersion();
        boolean changed = blockRepository.unblock(command.actorUserId(), command.targetUserId(), version);
        if (!changed) {
            return;
        }
        publishChangedWithCompensation(
                new BlockRelationChangedDomainEvent(command.actorUserId(), command.targetUserId(), false, version),
                () -> blockRepository.block(command.actorUserId(), command.targetUserId())
        );
    }

    public boolean hasBlocked(UUID userId, UUID targetUserId) {
        if (userId == null || targetUserId == null) {
            return false;
        }
        return blockRepository.hasBlocked(userId, targetUserId);
    }

    public boolean isEitherBlocked(UUID userIdA, UUID userIdB) {
        return blockDomainService.isEitherBlocked(userIdA, userIdB, blockRepository);
    }

    public List<UUID> listBlockedUserIds(UUID userId) {
        if (userId == null) {
            throw new com.nowcoder.community.common.exception.BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return blockRepository.listBlockedUserIds(userId);
    }

    public List<BlockRelationResult> scanBlockRelationsAfter(UUID afterBlockerUserId, UUID afterBlockedUserId, int limit) {
        if ((afterBlockerUserId == null) != (afterBlockedUserId == null)) {
            throw new IllegalArgumentException("afterBlockerUserId and afterBlockedUserId must be provided together");
        }
        return blockRepository.scanBlocksAfter(afterBlockerUserId, afterBlockedUserId, limit)
                .stream()
                .map(this::toResult)
                .toList();
    }

    public long currentBlockProjectionVersion() {
        return blockRepository.currentBlockProjectionVersion();
    }

    private void publishChangedWithCompensation(BlockRelationChangedDomainEvent event, Runnable rollback) {
        boolean needsExplicitCompensation = blockRepository.requiresExplicitCompensation()
                || followRepository.requiresExplicitCompensation();
        if (needsExplicitCompensation) {
            registerRollbackIfTxRolledBack(rollback);
        }
        try {
            eventPublisher.publishBlockRelationChanged(event);
        } catch (RuntimeException ex) {
            if (needsExplicitCompensation) {
                compensate(rollback);
            }
            throw ex;
        }
    }

    private BlockRelationResult toResult(BlockRelation relation) {
        return new BlockRelationResult(relation.blockerUserId(), relation.blockedUserId(), relation.version());
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
            log.warn("[block] rollback failed: {}", ex.toString());
        }
    }
}
