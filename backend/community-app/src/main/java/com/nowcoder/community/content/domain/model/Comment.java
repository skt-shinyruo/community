package com.nowcoder.community.content.domain.model;

import com.nowcoder.community.common.exception.BusinessException;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;

public class Comment {

    private static final int STATUS_ACTIVE = 0;
    private static final long EDIT_WINDOW_MILLIS = 15L * 60L * 1000L;

    private final CommentSnapshot snapshot;

    private Comment(CommentSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
    }

    public static Comment reconstitute(CommentSnapshot snapshot) {
        return new Comment(snapshot);
    }

    public CommentEdit editByAuthor(UUID actorUserId, UUID postId, String content, Date updateTime) {
        requireIdentity(actorUserId, postId);
        requireActive();
        if (!actorUserId.equals(snapshot.userId())) {
            throw new BusinessException(FORBIDDEN, "只能编辑自己的评论");
        }
        if (!postId.equals(snapshot.postId())) {
            throw new BusinessException(INVALID_ARGUMENT, "commentId 不属于该帖子");
        }
        Date effectiveUpdateTime = requireTime(updateTime, "updateTime");
        if (effectiveUpdateTime.getTime() - snapshot.createTime().getTime() > EDIT_WINDOW_MILLIS) {
            throw new BusinessException(FORBIDDEN, "已超过可编辑时间（15min）");
        }
        return new CommentEdit(snapshot.id(), snapshot.version(), content, effectiveUpdateTime);
    }

    public CommentDeletion deleteByAuthor(
            UUID actorUserId,
            UUID postId,
            String deletedReason,
            Date deletedTime
    ) {
        requireIdentity(actorUserId, postId);
        requireActive();
        if (!actorUserId.equals(snapshot.userId())) {
            throw new BusinessException(FORBIDDEN, "只能删除自己的评论");
        }
        if (!postId.equals(snapshot.postId())) {
            throw new BusinessException(INVALID_ARGUMENT, "commentId 不属于该帖子");
        }
        return deletion(actorUserId, deletedReason, deletedTime);
    }

    public CommentDeletion deleteByModerator(UUID actorUserId, String deletedReason, Date deletedTime) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        requireActive();
        return deletion(actorUserId, deletedReason, deletedTime);
    }

    public CommentSnapshot snapshot() {
        return snapshot;
    }

    public UUID getId() {
        return snapshot.id();
    }

    public boolean isActive() {
        return snapshot.status() == STATUS_ACTIVE;
    }

    public boolean isRootComment() {
        return snapshot.rootComment();
    }

    public UUID getPostId() {
        return snapshot.postId();
    }

    public UUID getUserId() {
        return snapshot.userId();
    }

    public UUID getRootCommentId() {
        return snapshot.rootCommentId();
    }

    public UUID getParentCommentId() {
        return snapshot.parentCommentId();
    }

    public UUID getReplyToUserId() {
        return snapshot.replyToUserId();
    }

    public String getContent() {
        return snapshot.content();
    }

    public int getStatus() {
        return snapshot.status();
    }

    public Date getCreateTime() {
        return snapshot.createTime();
    }

    public Date getUpdateTime() {
        return snapshot.updateTime();
    }

    public int getEditCount() {
        return snapshot.editCount();
    }

    public UUID getDeletedBy() {
        return snapshot.deletedBy();
    }

    public String getDeletedReason() {
        return snapshot.deletedReason();
    }

    public Date getDeletedTime() {
        return snapshot.deletedTime();
    }

    public long getVersion() {
        return snapshot.version();
    }

    private CommentDeletion deletion(UUID deletedBy, String deletedReason, Date deletedTime) {
        return new CommentDeletion(
                snapshot.id(),
                snapshot.version(),
                deletedBy,
                deletedReason,
                requireTime(deletedTime, "deletedTime")
        );
    }

    private void requireIdentity(UUID actorUserId, UUID postId) {
        if (actorUserId == null || postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }
    }

    private void requireActive() {
        if (!isActive()) {
            throw new BusinessException(COMMENT_NOT_FOUND);
        }
    }

    private Date requireTime(Date value, String name) {
        if (value == null) {
            throw new BusinessException(INVALID_ARGUMENT, name + " 非法");
        }
        return new Date(value.getTime());
    }
}
