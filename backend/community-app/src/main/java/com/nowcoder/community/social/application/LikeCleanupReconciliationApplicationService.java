package com.nowcoder.community.social.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.application.command.CleanupDeletedContentLikesCommand;
import com.nowcoder.community.social.application.command.ReconcileLikeCleanupCommand;
import com.nowcoder.community.social.application.result.LikeCleanupReconciliationResult;
import com.nowcoder.community.social.domain.model.LikeTargetState;
import com.nowcoder.community.social.domain.repository.LikeTargetStateRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.COMMENT;
import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class LikeCleanupReconciliationApplicationService {

    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final int MAX_BATCH_SIZE = 200;
    private final LikeTargetStateRepository targetStateRepository;
    private final LikeApplicationService likeApplicationService;
    private final LikeCleanupMetrics cleanupMetrics;

    public LikeCleanupReconciliationApplicationService(
            LikeTargetStateRepository targetStateRepository,
            LikeApplicationService likeApplicationService,
            LikeCleanupMetrics cleanupMetrics
    ) {
        this.targetStateRepository = targetStateRepository;
        this.likeApplicationService = likeApplicationService;
        this.cleanupMetrics = cleanupMetrics;
    }

    public LikeCleanupReconciliationResult reconcile(ReconcileLikeCleanupCommand command) {
        validate(command);
        UUID cursor = command.afterEntityId() == null ? ZERO_UUID : command.afterEntityId();
        List<LikeTargetState> targets = targetStateRepository.scanDeletedTargetsWithLikesAfter(
                command.entityType(),
                cursor,
                command.batchSize()
        );
        if (targets == null || targets.isEmpty()) {
            cleanupMetrics.setOrphanTargets(0L);
            return new LikeCleanupReconciliationResult(cursor, false, 0, 0, 0, 0);
        }

        int scanned = 0;
        int orphanTargets = 0;
        int cleaned = 0;
        int failed = 0;
        UUID nextCursor = cursor;
        for (LikeTargetState target : targets) {
            if (target == null || target.entityId() == null || !target.isDeleted()) {
                failed++;
                continue;
            }
            scanned++;
            nextCursor = target.entityId();
            orphanTargets++;
            try {
                likeApplicationService.cleanupDeletedContentLikes(new CleanupDeletedContentLikesCommand(
                        target.entityType(),
                        target.entityId(),
                        reconciliationEventId(target),
                        target.sourceVersion(),
                        target.deletedAt()
                ));
                cleaned++;
            } catch (RuntimeException ignored) {
                failed++;
            }
        }
        cleanupMetrics.setOrphanTargets(orphanTargets);
        return new LikeCleanupReconciliationResult(
                nextCursor,
                targets.size() >= command.batchSize(),
                scanned,
                orphanTargets,
                cleaned,
                failed
        );
    }

    private String reconciliationEventId(LikeTargetState target) {
        return "social-like-reconciliation:"
                + target.entityType()
                + ":"
                + target.entityId()
                + ":"
                + target.sourceVersion();
    }

    private void validate(ReconcileLikeCleanupCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if ((command.entityType() != POST && command.entityType() != COMMENT)
                || command.batchSize() <= 0
                || command.batchSize() > MAX_BATCH_SIZE) {
            throw new BusinessException(INVALID_ARGUMENT, "invalid like cleanup reconciliation command");
        }
    }
}
