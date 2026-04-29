package com.nowcoder.community.content.domain.service;

import com.nowcoder.community.common.constants.EntityTypes;
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
    void resolveCreateTargetShouldDefaultMissingEntityTypeToPostComment() {
        UUID postId = uuid(100);
        UUID postAuthorUserId = uuid(200);

        CommentDomainService.CreateTarget target = service.resolveCreateTarget(
                postId,
                null,
                null,
                null,
                postAuthorUserId,
                null
        );

        assertThat(target.entityType()).isEqualTo(EntityTypes.POST);
        assertThat(target.entityId()).isEqualTo(postId);
        assertThat(target.targetId()).isNull();
        assertThat(target.targetUserId()).isEqualTo(postAuthorUserId);
    }

    @Test
    void resolveCreateTargetShouldRejectReplyTargetThatIsNotFirstLevelCommentUnderPost() {
        UUID postId = uuid(100);
        UUID targetCommentId = uuid(300);
        CommentSnapshot nestedReply = snapshot(
                targetCommentId,
                uuid(301),
                EntityTypes.COMMENT,
                uuid(302),
                uuid(303),
                0,
                new Date()
        );

        assertThatThrownBy(() -> service.resolveCreateTarget(
                postId,
                EntityTypes.COMMENT,
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
    void assertEditableByAuthorShouldRejectEditsAfterFifteenMinutes() {
        Date createTime = new Date(1_000_000L);
        Date afterEditWindow = new Date(createTime.getTime() + 15L * 60 * 1000 + 1);
        UUID userId = uuid(100);
        UUID postId = uuid(200);
        CommentSnapshot comment = snapshot(
                uuid(300),
                userId,
                EntityTypes.POST,
                postId,
                null,
                0,
                createTime
        );

        assertThatThrownBy(() -> service.assertEditableByAuthor(comment, userId, postId, afterEditWindow, null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }

    private static CommentSnapshot snapshot(
            UUID id,
            UUID userId,
            int entityType,
            UUID entityId,
            UUID targetId,
            int status,
            Date createTime
    ) {
        return new CommentSnapshot(id, userId, entityType, entityId, targetId, "content", status, createTime, createTime, 0);
    }
}
