package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.CommentDataObject;

final class CommentPersistenceConverter {

    private CommentPersistenceConverter() {
    }

    static Comment toAggregate(CommentDataObject row) {
        return Comment.reconstitute(toSnapshot(row));
    }

    static CommentSnapshot toSnapshot(CommentDataObject row) {
        return new CommentSnapshot(
                row.getId(),
                row.getUserId(),
                row.getPostId(),
                row.getRootCommentId(),
                row.getParentCommentId(),
                row.getReplyToUserId(),
                row.getContent(),
                row.getStatus(),
                row.getCreateTime(),
                row.getUpdateTime(),
                row.getEditCount(),
                row.getDeletedBy(),
                row.getDeletedReason(),
                row.getDeletedTime(),
                row.getVersion()
        );
    }
}
