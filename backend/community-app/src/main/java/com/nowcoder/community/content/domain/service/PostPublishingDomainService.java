package com.nowcoder.community.content.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.model.PostDraft;
import com.nowcoder.community.content.domain.model.PostSnapshot;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;

public class PostPublishingDomainService {

    private static final long EDIT_WINDOW_MILLIS = 24L * 3600 * 1000;
    private static final int STATUS_DELETED = 2;

    public PostDraft createDraft(UUID userId, String title, String content, UUID categoryId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return new PostDraft(userId, title, content, categoryId, new Date());
    }

    public void assertEditableByAuthor(PostSnapshot post, UUID actorUserId, Date now) {
        assertOwnedActivePost(post, actorUserId, "只能编辑自己的帖子");
        if (post.createTime() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "帖子时间非法");
        }
        Date effectiveNow = now == null ? new Date() : now;
        if (effectiveNow.getTime() - post.createTime().getTime() > EDIT_WINDOW_MILLIS) {
            throw new BusinessException(FORBIDDEN, "已超过可编辑时间（24h）");
        }
    }

    public void assertDeletableByAuthor(PostSnapshot post, UUID actorUserId) {
        assertOwnedActivePost(post, actorUserId, "只能删除自己的帖子");
    }

    private void assertOwnedActivePost(PostSnapshot post, UUID actorUserId, String forbiddenMessage) {
        if (actorUserId == null || post == null || post.id() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }
        if (post.status() == STATUS_DELETED) {
            throw new BusinessException(POST_NOT_FOUND);
        }
        if (!actorUserId.equals(post.userId())) {
            throw new BusinessException(FORBIDDEN, forbiddenMessage);
        }
    }
}
