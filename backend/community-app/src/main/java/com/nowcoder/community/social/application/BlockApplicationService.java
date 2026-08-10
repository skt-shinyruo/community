package com.nowcoder.community.social.application;

import com.nowcoder.community.social.api.action.SocialInteractionActionApi;
import com.nowcoder.community.social.api.model.SocialBlockRelationView;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.social.domain.event.BlockRelationChangedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import com.nowcoder.community.social.domain.model.BlockRelation;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.domain.repository.FollowRepository;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;

@Service("socialBlockApplicationService")
public class BlockApplicationService implements SocialBlockQueryApi, SocialInteractionActionApi {

    private final BlockRepository blockRepository;
    private final FollowRepository followRepository;
    private final BlockDomainService blockDomainService;
    private final SocialDomainEventPublisher eventPublisher;
    private final Clock clock;

    public BlockApplicationService(
            BlockRepository blockRepository,
            FollowRepository followRepository,
            BlockDomainService blockDomainService,
            SocialDomainEventPublisher eventPublisher,
            Clock clock
    ) {
        this.blockRepository = Objects.requireNonNull(blockRepository, "blockRepository must not be null");
        this.followRepository = Objects.requireNonNull(followRepository, "followRepository must not be null");
        this.blockDomainService = Objects.requireNonNull(blockDomainService, "blockDomainService must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public void block(BlockCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        blockDomainService.validateBlock(command.actorUserId(), command.targetUserId());
        blockRepository.lockUserPair(command.actorUserId(), command.targetUserId());
        long version = blockRepository.nextBlockProjectionVersion();
        boolean changed = blockRepository.block(command.actorUserId(), command.targetUserId(), version);
        followRepository.unfollow(command.actorUserId(), USER, command.targetUserId());
        followRepository.unfollow(command.targetUserId(), USER, command.actorUserId());
        if (!changed) {
            return;
        }
        Instant occurredAt = Instant.now(clock);
        eventPublisher.publishBlockRelationChanged(
                new BlockRelationChangedDomainEvent(command.actorUserId(), command.targetUserId(), true, occurredAt, version)
        );
    }

    @Transactional
    public void unblock(UnblockCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        blockDomainService.validateUnblock(command.actorUserId(), command.targetUserId());
        blockRepository.lockUserPair(command.actorUserId(), command.targetUserId());
        long version = blockRepository.nextBlockProjectionVersion();
        boolean changed = blockRepository.unblock(command.actorUserId(), command.targetUserId(), version);
        if (!changed) {
            return;
        }
        Instant occurredAt = Instant.now(clock);
        eventPublisher.publishBlockRelationChanged(
                new BlockRelationChangedDomainEvent(command.actorUserId(), command.targetUserId(), false, occurredAt, version)
        );
    }

    @Override
    public boolean hasBlocked(UUID userId, UUID targetUserId) {
        if (userId == null || targetUserId == null) {
            return false;
        }
        return blockRepository.hasBlocked(userId, targetUserId);
    }

    @Override
    public boolean isEitherBlocked(UUID userIdA, UUID userIdB) {
        return blockDomainService.isEitherBlocked(userIdA, userIdB, blockRepository);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void assertInteractionAllowed(UUID actorUserId, UUID targetUserId) {
        if (actorUserId == null || targetUserId == null || actorUserId.equals(targetUserId)) {
            return;
        }
        blockRepository.lockUserPair(actorUserId, targetUserId);
        if (blockDomainService.isEitherBlocked(actorUserId, targetUserId, blockRepository)) {
            throw new com.nowcoder.community.common.exception.BusinessException(
                    FORBIDDEN, "双方存在拉黑关系，无法执行该操作");
        }
    }

    public List<UUID> listBlockedUserIds(UUID userId) {
        if (userId == null) {
            throw new com.nowcoder.community.common.exception.BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return blockRepository.listBlockedUserIds(userId);
    }

    @Override
    public List<SocialBlockRelationView> scanBlockRelationsAtVersionAfter(
            long snapshotVersion,
            UUID afterBlockerUserId,
            UUID afterBlockedUserId,
            int limit
    ) {
        if (snapshotVersion < 0L) {
            throw new IllegalArgumentException("snapshotVersion must be non-negative");
        }
        if ((afterBlockerUserId == null) != (afterBlockedUserId == null)) {
            throw new IllegalArgumentException("afterBlockerUserId and afterBlockedUserId must be provided together");
        }
        return blockRepository.scanBlocksAtVersionAfter(
                        snapshotVersion,
                        afterBlockerUserId,
                        afterBlockedUserId,
                        limit
                )
                .stream()
                .map(this::toResult)
                .toList();
    }

    @Override
    public long currentBlockProjectionVersion() {
        return blockRepository.currentBlockProjectionVersion();
    }

    private SocialBlockRelationView toResult(BlockRelation relation) {
        return new SocialBlockRelationView(relation.blockerUserId(), relation.blockedUserId(), relation.version());
    }

    public record BlockCommand(UUID actorUserId, UUID targetUserId) {
    }

    public record UnblockCommand(UUID actorUserId, UUID targetUserId) {
    }

}
