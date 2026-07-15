package com.nowcoder.community.content.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
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
    void resolveCreateTargetShouldCreateRootCommentWhenParentMissing() {
        UUID postId = uuid(100);
        UUID postAuthorUserId = uuid(200);

        CommentDomainService.CreateTarget target = service.resolveCreateTarget(
                postId,
                null,
                null,
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
    void resolveCreateTargetShouldRejectReplyTargetThatIsNotRootCommentUnderPost() {
        UUID postId = uuid(100);
        UUID targetCommentId = uuid(300);
        CommentSnapshot nestedReply = snapshot(
                targetCommentId,
                uuid(301),
                postId,
                uuid(302),
                uuid(303),
                uuid(304),
                0,
                new Date()
        );

        assertThatThrownBy(() -> service.resolveCreateTarget(
                postId,
                targetCommentId,
                null,
                uuid(200),
                nestedReply
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void resolveCreateTargetShouldRejectNullPostId() {
        assertThatThrownBy(() -> service.resolveCreateTarget(
                null,
                null,
                null,
                uuid(200),
                null
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void resolveCreateTargetShouldRejectMismatchedTargetSnapshot() {
        UUID postId = uuid(100);
        UUID targetCommentId = uuid(300);
        CommentSnapshot targetComment = snapshot(
                uuid(301),
                uuid(302),
                postId,
                uuid(301),
                null,
                null,
                0,
                new Date()
        );

        assertThatThrownBy(() -> service.resolveCreateTarget(
                postId,
                targetCommentId,
                null,
                uuid(200),
                targetComment
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void resolveCreateTargetShouldUseExplicitReplyTargetUserForReply() {
        UUID postId = uuid(100);
        UUID targetCommentId = uuid(300);
        UUID targetUserId = uuid(302);
        CommentSnapshot targetComment = snapshot(
                targetCommentId,
                uuid(401),
                postId,
                targetCommentId,
                null,
                null,
                0,
                new Date()
        );

        CommentDomainService.CreateTarget target = service.resolveCreateTarget(
                postId,
                targetCommentId,
                targetUserId,
                uuid(200),
                targetComment
        );

        assertThat(target.rootCommentId()).isEqualTo(targetCommentId);
        assertThat(target.parentCommentId()).isEqualTo(targetCommentId);
        assertThat(target.replyToUserId()).isEqualTo(targetUserId);
        assertThat(target.targetUserId()).isEqualTo(targetUserId);
    }

    private static CommentSnapshot snapshot(
            UUID id,
            UUID userId,
            UUID postId,
            UUID rootCommentId,
            UUID parentCommentId,
            UUID replyToUserId,
            int status,
            Date createTime
    ) {
        return new CommentSnapshot(
                id,
                userId,
                postId,
                rootCommentId,
                parentCommentId,
                replyToUserId,
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
