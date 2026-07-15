package com.nowcoder.community.interaction.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.content.api.model.ResolvedContentRef;
import com.nowcoder.community.content.api.query.ContentEntityQueryApi;
import com.nowcoder.community.interaction.application.command.SetLikeInteractionCommand;
import com.nowcoder.community.interaction.application.result.LikeInteractionResult;
import com.nowcoder.community.social.api.action.SocialLikeActionApi;
import com.nowcoder.community.social.api.model.ResolvedLikeTargetView;
import com.nowcoder.community.social.api.model.SocialLikeResultView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.COMMENT;
import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeInteractionApplicationServiceTest {

    @Mock
    private UserLookupQueryApi userLookupQueryApi;
    @Mock
    private ContentEntityQueryApi contentEntityQueryApi;
    @Mock
    private SocialLikeActionApi socialLikeActionApi;

    @Test
    void setLikeShouldResolveUserTargetBeforeCallingSocial() {
        UUID actorUserId = uuid(1);
        UUID targetUserId = uuid(2);
        ResolvedLikeTargetView resolvedTarget = new ResolvedLikeTargetView(targetUserId, null);
        when(userLookupQueryApi.getSummaryById(targetUserId))
                .thenReturn(new UserSummaryView(targetUserId, "target", "header", 0));
        when(socialLikeActionApi.setLike(actorUserId, USER, targetUserId, true, resolvedTarget))
                .thenReturn(new SocialLikeResultView(true, 4L));

        LikeInteractionResult result = service().setLike(
                new SetLikeInteractionCommand(actorUserId, USER, targetUserId, true)
        );

        assertThat(result).isEqualTo(new LikeInteractionResult(true, 4L));
        verify(userLookupQueryApi).getSummaryById(targetUserId);
        verifyNoInteractions(contentEntityQueryApi);
        verify(socialLikeActionApi).setLike(actorUserId, USER, targetUserId, true, resolvedTarget);
    }

    @Test
    void setLikeShouldConvertPostOwnerModelToSocialOwnedTargetView() {
        UUID actorUserId = uuid(1);
        UUID postId = uuid(11);
        UUID postOwnerId = uuid(7);
        when(contentEntityQueryApi.resolve(POST, postId))
                .thenReturn(new ResolvedContentRef(postOwnerId, postId));
        when(socialLikeActionApi.setLike(
                actorUserId,
                POST,
                postId,
                true,
                new ResolvedLikeTargetView(postOwnerId, postId)
        )).thenReturn(new SocialLikeResultView(true, 8L));

        LikeInteractionResult result = service().setLike(
                new SetLikeInteractionCommand(actorUserId, POST, postId, true)
        );

        assertThat(result).isEqualTo(new LikeInteractionResult(true, 8L));
        verify(contentEntityQueryApi).resolve(POST, postId);
        verifyNoInteractions(userLookupQueryApi);
        verify(socialLikeActionApi).setLike(
                actorUserId,
                POST,
                postId,
                true,
                new ResolvedLikeTargetView(postOwnerId, postId)
        );
    }

    @Test
    void setLikeShouldConvertCommentOwnerAndParentPostToSocialOwnedTargetView() {
        UUID actorUserId = uuid(1);
        UUID commentId = uuid(21);
        UUID commentOwnerId = uuid(9);
        UUID parentPostId = uuid(11);
        when(contentEntityQueryApi.resolve(COMMENT, commentId))
                .thenReturn(new ResolvedContentRef(commentOwnerId, parentPostId));
        when(socialLikeActionApi.setLike(
                actorUserId,
                COMMENT,
                commentId,
                false,
                new ResolvedLikeTargetView(commentOwnerId, parentPostId)
        )).thenReturn(new SocialLikeResultView(false, 2L));

        LikeInteractionResult result = service().setLike(
                new SetLikeInteractionCommand(actorUserId, COMMENT, commentId, false)
        );

        assertThat(result).isEqualTo(new LikeInteractionResult(false, 2L));
        verify(contentEntityQueryApi).resolve(COMMENT, commentId);
        verifyNoInteractions(userLookupQueryApi);
        verify(socialLikeActionApi).setLike(
                actorUserId,
                COMMENT,
                commentId,
                false,
                new ResolvedLikeTargetView(commentOwnerId, parentPostId)
        );
    }

    @Test
    void setLikeShouldNotCallSocialWhenUserTargetDoesNotExist() {
        UUID actorUserId = uuid(1);
        UUID targetUserId = uuid(404);
        when(userLookupQueryApi.getSummaryById(targetUserId)).thenReturn(null);

        assertThatThrownBy(() -> service().setLike(
                new SetLikeInteractionCommand(actorUserId, USER, targetUserId, true)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.NOT_FOUND));

        verifyNoInteractions(contentEntityQueryApi, socialLikeActionApi);
    }

    @Test
    void setLikeShouldNotCallSocialWhenContentResolutionFails() {
        UUID actorUserId = uuid(1);
        UUID postId = uuid(404);
        when(contentEntityQueryApi.resolve(POST, postId))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND, "post not found"));

        assertThatThrownBy(() -> service().setLike(
                new SetLikeInteractionCommand(actorUserId, POST, postId, true)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.NOT_FOUND));

        verifyNoInteractions(userLookupQueryApi, socialLikeActionApi);
    }

    private LikeInteractionApplicationService service() {
        return new LikeInteractionApplicationService(
                userLookupQueryApi,
                contentEntityQueryApi,
                socialLikeActionApi
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
