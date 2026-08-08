package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class CommentThreadCleanupApplicationService {

    private final CommentRepository commentRepository;
    private final CommentDeletionTransactionOperations deletionOperations;

    public CommentThreadCleanupApplicationService(
            CommentRepository commentRepository,
            CommentDeletionTransactionOperations deletionOperations
    ) {
        this.commentRepository = commentRepository;
        this.deletionOperations = deletionOperations;
    }

    public CleanupResult reconcile(int limit) {
        int normalizedLimit = Math.min(500, Math.max(1, limit));
        List<UUID> roots = commentRepository.findDeletedRootIdsWithActiveReplies(normalizedLimit);
        int completed = 0;
        int failed = 0;
        for (UUID rootId : roots) {
            try {
                cleanupRoot(rootId);
                completed++;
            } catch (RuntimeException exception) {
                failed++;
            }
        }
        return new CleanupResult(roots.size(), completed, failed);
    }

    void cleanupRoot(UUID rootId) {
        CommentSnapshot root = commentRepository.findSnapshot(rootId).orElse(null);
        if (root == null || !root.rootComment() || root.status() == 0) {
            return;
        }
        UUID deletedBy = root.deletedBy();
        Date deletedTime = root.deletedTime();
        if (deletedBy == null || deletedTime == null) {
            throw new IllegalStateException("deleted root comment is missing cleanup metadata");
        }
        String deletedReason = StringUtils.hasText(root.deletedReason())
                ? root.deletedReason() : "root_deleted_cleanup";
        while (true) {
            CommentDeletionResult batch = deletionOperations.deleteReplyBatch(
                    root.id(), root.postId(), deletedBy, deletedReason, deletedTime,
                    CommentDeletionTransactionOperations.REPLY_BATCH_SIZE);
            if (batch == null || !batch.changed()) {
                return;
            }
        }
    }

    public record CleanupResult(int scanned, int completed, int failed) {
    }
}
