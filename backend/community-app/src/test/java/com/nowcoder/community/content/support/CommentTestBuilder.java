package com.nowcoder.community.content.support;

import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.CommentDataObject;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;

public final class CommentTestBuilder {

    private UUID id = uuid(9901);
    private UUID userId = uuid(9902);
    private UUID postId = uuid(9903);
    private UUID rootCommentId;
    private UUID parentCommentId;
    private UUID replyToUserId;
    private String content = "comment";
    private int status;
    private Date createTime = new Date(1_000_000L);
    private Date updateTime;
    private int editCount;
    private UUID deletedBy;
    private String deletedReason;
    private Date deletedTime;
    private long version;

    private CommentTestBuilder() {
    }

    public static CommentTestBuilder aComment() {
        return new CommentTestBuilder();
    }

    public CommentTestBuilder id(UUID value) {
        id = value;
        return this;
    }

    public CommentTestBuilder userId(UUID value) {
        userId = value;
        return this;
    }

    public CommentTestBuilder postId(UUID value) {
        postId = value;
        return this;
    }

    public CommentTestBuilder rootCommentId(UUID value) {
        rootCommentId = value;
        return this;
    }

    public CommentTestBuilder parentCommentId(UUID value) {
        parentCommentId = value;
        return this;
    }

    public CommentTestBuilder replyToUserId(UUID value) {
        replyToUserId = value;
        return this;
    }

    public CommentTestBuilder content(String value) {
        content = value;
        return this;
    }

    public CommentTestBuilder status(int value) {
        status = value;
        return this;
    }

    public CommentTestBuilder createTime(Date value) {
        createTime = value;
        return this;
    }

    public CommentTestBuilder updateTime(Date value) {
        updateTime = value;
        return this;
    }

    public CommentTestBuilder editCount(int value) {
        editCount = value;
        return this;
    }

    public CommentTestBuilder deletedBy(UUID value) {
        deletedBy = value;
        return this;
    }

    public CommentTestBuilder deletedReason(String value) {
        deletedReason = value;
        return this;
    }

    public CommentTestBuilder deletedTime(Date value) {
        deletedTime = value;
        return this;
    }

    public CommentTestBuilder version(long value) {
        version = value;
        return this;
    }

    public Comment build() {
        return Comment.reconstitute(snapshot());
    }

    public CommentDataObject buildDataObject() {
        CommentSnapshot snapshot = snapshot();
        CommentDataObject row = new CommentDataObject();
        row.setId(snapshot.id());
        row.setUserId(snapshot.userId());
        row.setPostId(snapshot.postId());
        row.setRootCommentId(snapshot.rootCommentId());
        row.setParentCommentId(snapshot.parentCommentId());
        row.setReplyToUserId(snapshot.replyToUserId());
        row.setContent(snapshot.content());
        row.setStatus(snapshot.status());
        row.setCreateTime(snapshot.createTime());
        row.setUpdateTime(snapshot.updateTime());
        row.setEditCount(snapshot.editCount());
        row.setDeletedBy(snapshot.deletedBy());
        row.setDeletedReason(snapshot.deletedReason());
        row.setDeletedTime(snapshot.deletedTime());
        row.setVersion(snapshot.version());
        return row;
    }

    private CommentSnapshot snapshot() {
        UUID effectiveRootCommentId = rootCommentId;
        if (effectiveRootCommentId == null) {
            effectiveRootCommentId = parentCommentId == null ? id : parentCommentId;
        }
        return new CommentSnapshot(
                id,
                userId,
                postId,
                effectiveRootCommentId,
                parentCommentId,
                replyToUserId,
                content,
                status,
                createTime,
                updateTime,
                editCount,
                deletedBy,
                deletedReason,
                deletedTime,
                version
        );
    }
}
