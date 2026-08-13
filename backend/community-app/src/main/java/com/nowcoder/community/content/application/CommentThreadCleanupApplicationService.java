package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class CommentThreadCleanupApplicationService {

    private static final Logger log = LoggerFactory.getLogger(CommentThreadCleanupApplicationService.class);

    private final CommentRepository commentRepository;
    private final CommentDeletionTransactionOperations deletionOperations;

    public CommentThreadCleanupApplicationService(
            CommentRepository commentRepository,
            CommentDeletionTransactionOperations deletionOperations
    ) {
        this.commentRepository = commentRepository;
        this.deletionOperations = deletionOperations;
    }

    public CleanupResult reconcile(int limit, int maxBatchesPerRoot) {
        int normalizedLimit = Math.min(500, Math.max(1, limit));
        int normalizedBatchBudget = Math.min(100, Math.max(1, maxBatchesPerRoot));
        List<UUID> roots = commentRepository.findDeletedRootIdsWithActiveReplies(normalizedLimit);
        int completed = 0;
        int deferred = 0;
        List<UUID> failedRootIds = new ArrayList<>();
        for (UUID rootId : roots) {
            try {
                if (cleanupRoot(rootId, normalizedBatchBudget)) {
                    completed++;
                } else {
                    deferred++;
                }
            } catch (RuntimeException exception) {
                failedRootIds.add(rootId);
                log.warn("[content-comment] cleanup root failed rootId={}", rootId, exception);
            }
        }
        return new CleanupResult(roots.size(), completed, deferred, failedRootIds);
    }

    boolean cleanupRoot(UUID rootId, int maxBatches) {
        CommentSnapshot root = commentRepository.findSnapshot(rootId).orElse(null);
        if (root == null || !root.rootComment() || root.status() == 0) {
            return true;
        }
        UUID deletedBy = root.deletedBy();
        Date deletedTime = root.deletedTime();
        if (deletedBy == null || deletedTime == null) {
            throw new IllegalStateException("deleted root comment is missing cleanup metadata");
        }
        String deletedReason = StringUtils.hasText(root.deletedReason())
                ? root.deletedReason() : "root_deleted_cleanup";
        for (int batchIndex = 0; batchIndex < maxBatches; batchIndex++) {
            CommentDeletionResult batch = deletionOperations.deleteReplyBatch(
                    root.id(), root.postId(), deletedBy, deletedReason, deletedTime,
                    CommentDeletionTransactionOperations.REPLY_BATCH_SIZE);
            if (batch == null || !batch.changed()) {
                return true;
            }
        }
        return !commentRepository.hasActiveReplies(root.id());
    }

    public record CleanupResult(
            int scanned,
            int completed,
            int deferred,
            List<UUID> failedRootIds
    ) {

        public CleanupResult {
            failedRootIds = failedRootIds == null ? List.of() : List.copyOf(failedRootIds);
        }

        public int failed() {
            return failedRootIds.size();
        }
    }
}
