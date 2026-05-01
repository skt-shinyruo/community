package com.nowcoder.community.content.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.model.PostSnapshot;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;

public class PostModerationDomainService {

    private static final int STATUS_DELETED = 2;

    public void assertCanModeratePost(UUID actorUserId, PostSnapshot post) {
        assertValidActorAndPost(actorUserId, post);
        if (post.status() == STATUS_DELETED) {
            throw new BusinessException(POST_NOT_FOUND);
        }
    }

    public boolean shouldAdminDelete(UUID actorUserId, PostSnapshot post) {
        assertValidActorAndPost(actorUserId, post);
        return post.status() != STATUS_DELETED;
    }

    private void assertValidActorAndPost(UUID actorUserId, PostSnapshot post) {
        if (actorUserId == null || post == null || post.id() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }
    }
}
