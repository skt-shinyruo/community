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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.POST;

@Component
public class LikeCleanupTransactionOperations {

    private static final UUID FIRST_ACTOR_ID = new UUID(0L, 0L);

    private final LikeRepository likeRepository;
    private final LikeTargetStateRepository targetStateRepository;
    private final LikeDomainService likeDomainService;
    private final SocialDomainEventPublisher eventPublisher;

    public LikeCleanupTransactionOperations(
            LikeRepository likeRepository,
            LikeTargetStateRepository targetStateRepository,
            LikeDomainService likeDomainService,
            SocialDomainEventPublisher eventPublisher
    ) {
        this.likeRepository = likeRepository;
        this.targetStateRepository = targetStateRepository;
        this.likeDomainService = likeDomainService;
        this.eventPublisher = eventPublisher;
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
                FIRST_ACTOR_ID,
                limit
        );
        if (page == null || page.isEmpty()) {
            return CleanupBatchResult.empty();
        }
        long removed = 0L;
        for (LikeRelation relation : page) {
            if (!likeRepository.removeLike(relation)) {
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
        return new CleanupBatchResult(page.size(), removed);
    }

    public record CleanupBatchResult(int scanned, long removed) {

        static CleanupBatchResult empty() {
            return new CleanupBatchResult(0, 0L);
        }
    }
}
