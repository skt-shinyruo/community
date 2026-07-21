package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentDeletion;
import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentEdit;
import com.nowcoder.community.content.domain.model.CommentReplyContext;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.model.CommentThreadDeletion;
import com.nowcoder.community.content.domain.model.CommentTransitionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository {

    UUID create(CommentDraft draft);

    CommentSnapshot getRequiredSnapshot(UUID commentId);

    Optional<CommentSnapshot> findSnapshot(UUID commentId);

    Optional<CommentSnapshot> findActiveSnapshot(UUID commentId);

    Optional<CommentReplyContext> lockReplyContext(UUID postId, UUID directParentCommentId);

    List<CommentSnapshot> getActiveThreadSnapshots(UUID rootCommentId);

    CommentTransitionStatus apply(CommentEdit edit);

    CommentDeletionResult apply(CommentDeletion deletion);

    CommentDeletionResult apply(CommentThreadDeletion deletion);
}
