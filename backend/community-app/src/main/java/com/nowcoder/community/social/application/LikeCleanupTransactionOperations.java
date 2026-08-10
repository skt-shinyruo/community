package com.nowcoder.community.social.application;

import com.nowcoder.community.social.application.command.CleanupDeletedContentLikesCommand;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import com.nowcoder.community.social.domain.model.LikeRelation;
import com.nowcoder.community.social.domain.model.LikeTargetState;
import com.nowcoder.community.social.domain.model.ResolvedSocialEntity;
import com.nowcoder.community.social.domain.repository.LikeRepository;
import com.nowcoder.community.social.domain.repository.LikeTargetStateRepository;
import com.nowcoder.community.social.domain.service.LikeDomainService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.POST;

@Component
public class LikeCleanupTransactionOperations {

    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private final LikeRepository likeRepository;
    private final LikeTargetStateRepository targetStateRepository;
    private final LikeDomainService likeDomainService;
    private final SocialDomainEventPublisher eventPublisher;
    private final Clock clock;

    public LikeCleanupTransactionOperations(
            LikeRepository likeRepository,
            LikeTargetStateRepository targetStateRepository,
            LikeDomainService likeDomainService,
            SocialDomainEventPublisher eventPublisher,
            Clock clock
    ) {
        this.likeRepository = Objects.requireNonNull(likeRepository, "likeRepository must not be null");
        this.targetStateRepository = Objects.requireNonNull(
                targetStateRepository, "targetStateRepository must not be null");
        this.likeDomainService = Objects.requireNonNull(likeDomainService, "likeDomainService must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistDeletionFence(CleanupDeletedContentLikesCommand command) {
        targetStateRepository.insertActiveIfAbsent(command.entityType(), command.entityId());
        LikeTargetState current = targetStateRepository.findForUpdate(command.entityType(), command.entityId());
        LikeTargetState updated = current.applyDeletion(
                command.sourceEventId(),
                command.sourceVersion(),
                command.deletedAt()
        );
        if (updated != current && !targetStateRepository.saveIfNewer(updated)) {
            throw new IllegalStateException("failed to advance like target deletion fence");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CleanupBatchResult cleanupBatch(int entityType, UUID entityId, int limit) {
        LikeTargetState target = targetStateRepository.findForUpdate(entityType, entityId);
        if (target == null || !target.isDeleted()) {
            throw new IllegalStateException("like target deletion fence missing");
        }
        List<LikeRelation> page = likeRepository.scanLikesByEntity(
                entityType,
                entityId,
                ZERO_UUID,
                limit
        );
        if (page == null || page.isEmpty()) {
            return CleanupBatchResult.empty();
        }
        return removeRelations(page);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CleanupBatchResult cleanupCommentLikesByPostBatch(UUID postId, int limit) {
        LikeTargetState target = targetStateRepository.findForUpdate(POST, postId);
        if (target == null || !target.isDeleted()) {
            throw new IllegalStateException("post like target deletion fence missing");
        }
        List<LikeRelation> page = likeRepository.scanCommentLikesByPost(
                postId,
                ZERO_UUID,
                ZERO_UUID,
                limit
        );
        if (page == null || page.isEmpty()) {
            return CleanupBatchResult.empty();
        }
        return removeRelations(page);
    }

    private CleanupBatchResult removeRelations(List<LikeRelation> page) {
        long removed = 0L;
        for (LikeRelation relation : page) {
            if (!likeRepository.removeLike(relation)) {
                continue;
            }
            if (relation.entityUserId() != null) {
                likeRepository.incrementUserLikeCount(relation.entityUserId(), -1L);
            }
            long relationVersion = likeRepository.nextRelationEventVersion(
                    relation.actorUserId(), relation.entityType(), relation.entityId());
            LikeChangedDomainEvent event = likeDomainService.likeChangedEvent(
                    relation,
                    new ResolvedSocialEntity(relation.entityUserId(), relation.postId()),
                    relationVersion,
                    false,
                    Instant.now(clock)
            );
            eventPublisher.publishLikeChanged(event);
            removed++;
        }
        return new CleanupBatchResult(page.size(), removed);
    }

    public record CleanupBatchResult(int scanned, long removed) {

        static CleanupBatchResult empty() {
            return new CleanupBatchResult(0, 0L);
        }
    }
}
