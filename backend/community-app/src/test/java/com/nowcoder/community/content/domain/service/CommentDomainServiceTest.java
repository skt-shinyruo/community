package com.nowcoder.community.content.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.content.domain.model.CommentReplyContext;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentDomainServiceTest {

    private final CommentDomainService service = new CommentDomainService();

    @Test
    void resolveCreateTargetShouldCreateTopLevelCommentWithoutReplyContext() {
        UUID postId = uuid(100);
        UUID postAuthorUserId = uuid(200);

        CommentDomainService.CreateTarget target = service.resolveCreateTarget(
                postId,
                postAuthorUserId,
                null
        );

        assertThat(target.postId()).isEqualTo(postId);
        assertThat(target.rootCommentId()).isNull();
        assertThat(target.parentCommentId()).isNull();
        assertThat(target.replyToUserId()).isNull();
        assertThat(target.targetUserId()).isEqualTo(postAuthorUserId);
    }

    @Test
    void resolveCreateTargetShouldDeriveDirectRootReplyFromLockedRootAuthor() {
        UUID postId = uuid(100);
        UUID rootId = uuid(300);
        UUID rootAuthorId = uuid(301);
        CommentSnapshot root = root(rootId, rootAuthorId, postId, 0);

        CommentDomainService.CreateTarget target = service.resolveCreateTarget(
                postId,
                uuid(200),
                new CommentReplyContext(root, root)
        );

        assertThat(target.rootCommentId()).isEqualTo(rootId);
        assertThat(target.parentCommentId()).isEqualTo(rootId);
        assertThat(target.replyToUserId()).isEqualTo(rootAuthorId);
        assertThat(target.targetUserId()).isEqualTo(rootAuthorId);
    }

    @Test
    void resolveCreateTargetShouldDeriveNestedReplyFromLockedDirectParentAuthor() {
        UUID postId = uuid(100);
        UUID rootId = uuid(300);
        UUID rootAuthorId = uuid(301);
        UUID directParentId = uuid(302);
        UUID directParentAuthorId = uuid(303);
        CommentSnapshot root = root(rootId, rootAuthorId, postId, 0);
        CommentSnapshot directParent = reply(
                directParentId,
                directParentAuthorId,
                postId,
                rootId,
                rootId,
                0
        );

        CommentDomainService.CreateTarget target = service.resolveCreateTarget(
                postId,
                uuid(200),
                new CommentReplyContext(directParent, root)
        );

        assertThat(target.rootCommentId()).isEqualTo(rootId);
        assertThat(target.parentCommentId()).isEqualTo(directParentId);
        assertThat(target.replyToUserId()).isEqualTo(directParentAuthorId);
        assertThat(target.targetUserId()).isEqualTo(directParentAuthorId);
    }

    @Test
    void resolveCreateTargetShouldRejectNullPostId() {
        assertThatThrownBy(() -> service.resolveCreateTarget(null, uuid(200), null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void resolveCreateTargetShouldHideInactiveDirectParent() {
        UUID postId = uuid(100);
        UUID rootId = uuid(300);
        CommentSnapshot root = root(rootId, uuid(301), postId, 0);
        CommentSnapshot inactiveParent = reply(uuid(302), uuid(303), postId, rootId, rootId, 1);

        assertHiddenNotFound(() -> service.resolveCreateTarget(
                postId,
                uuid(200),
                new CommentReplyContext(inactiveParent, root)
        ));
    }

    @Test
    void resolveCreateTargetShouldHideInactiveRoot() {
        UUID postId = uuid(100);
        UUID rootId = uuid(300);
        CommentSnapshot inactiveRoot = root(rootId, uuid(301), postId, 1);
        CommentSnapshot directParent = reply(uuid(302), uuid(303), postId, rootId, rootId, 0);

        assertHiddenNotFound(() -> service.resolveCreateTarget(
                postId,
                uuid(200),
                new CommentReplyContext(directParent, inactiveRoot)
        ));
    }

    @Test
    void resolveCreateTargetShouldHidePostMismatchInEitherLockedFact() {
        UUID postId = uuid(100);
        UUID otherPostId = uuid(101);
        UUID rootId = uuid(300);
        CommentSnapshot root = root(rootId, uuid(301), postId, 0);
        CommentSnapshot wrongDirectPost = reply(uuid(302), uuid(303), otherPostId, rootId, rootId, 0);
        CommentSnapshot wrongRootPost = root(rootId, uuid(301), otherPostId, 0);
        CommentSnapshot directParent = reply(uuid(302), uuid(303), postId, rootId, rootId, 0);

        assertHiddenNotFound(() -> service.resolveCreateTarget(
                postId,
                uuid(200),
                new CommentReplyContext(wrongDirectPost, root)
        ));
        assertHiddenNotFound(() -> service.resolveCreateTarget(
                postId,
                uuid(200),
                new CommentReplyContext(directParent, wrongRootPost)
        ));
    }

    @Test
    void resolveCreateTargetShouldHideMalformedRootAndThreadMismatch() {
        UUID postId = uuid(100);
        UUID rootId = uuid(300);
        CommentSnapshot malformedRoot = reply(rootId, uuid(301), postId, rootId, uuid(399), 0);
        CommentSnapshot root = root(rootId, uuid(301), postId, 0);
        CommentSnapshot wrongThreadParent = reply(
                uuid(302),
                uuid(303),
                postId,
                uuid(398),
                rootId,
                0
        );

        assertHiddenNotFound(() -> service.resolveCreateTarget(
                postId,
                uuid(200),
                new CommentReplyContext(root, malformedRoot)
        ));
        assertHiddenNotFound(() -> service.resolveCreateTarget(
                postId,
                uuid(200),
                new CommentReplyContext(wrongThreadParent, root)
        ));
    }

    private static void assertHiddenNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    private static CommentSnapshot root(UUID id, UUID userId, UUID postId, int status) {
        return snapshot(id, userId, postId, id, null, status);
    }

    private static CommentSnapshot reply(
            UUID id,
            UUID userId,
            UUID postId,
            UUID rootCommentId,
            UUID parentCommentId,
            int status
    ) {
        return snapshot(id, userId, postId, rootCommentId, parentCommentId, status);
    }

    private static CommentSnapshot snapshot(
            UUID id,
            UUID userId,
            UUID postId,
            UUID rootCommentId,
            UUID parentCommentId,
            int status
    ) {
        Date createTime = new Date();
        return new CommentSnapshot(
                id,
                userId,
                postId,
                rootCommentId,
                parentCommentId,
                null,
                "content",
                status,
                createTime,
                createTime,
                0,
                null,
                null,
                null,
                7L
        );
    }
}
