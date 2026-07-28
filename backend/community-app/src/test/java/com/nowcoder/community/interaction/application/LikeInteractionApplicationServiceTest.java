package com.nowcoder.community.interaction.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.content.api.model.ResolvedContentRef;
import com.nowcoder.community.content.api.query.ContentEntityQueryApi;
import com.nowcoder.community.interaction.application.LikeInteractionApplicationService.LikeResult;
import com.nowcoder.community.interaction.application.LikeInteractionApplicationService.SetLikeCommand;
import com.nowcoder.community.social.api.action.SocialLikeActionApi;
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
        SocialLikeActionApi.SetLikeCommand socialCommand = new SocialLikeActionApi.SetLikeCommand(
                actorUserId, USER, targetUserId, true, targetUserId, null
        );
        when(userLookupQueryApi.getSummaryById(targetUserId))
                .thenReturn(new UserSummaryView(targetUserId, "target", "header", 0));
        when(socialLikeActionApi.setLike(socialCommand))
                .thenReturn(new SocialLikeActionApi.LikeResult(true, 4L));

        LikeResult result = service().setLike(
                new SetLikeCommand(actorUserId, USER, targetUserId, true)
        );

        assertThat(result).isEqualTo(new LikeResult(true, 4L));
        verify(userLookupQueryApi).getSummaryById(targetUserId);
        verifyNoInteractions(contentEntityQueryApi);
        verify(socialLikeActionApi).setLike(socialCommand);
    }

    @Test
    void setLikeShouldConvertPostOwnerModelToSocialOwnedTargetView() {
        UUID actorUserId = uuid(1);
        UUID postId = uuid(11);
        UUID postOwnerId = uuid(7);
        when(contentEntityQueryApi.resolve(POST, postId))
                .thenReturn(new ResolvedContentRef(postOwnerId, postId));
        SocialLikeActionApi.SetLikeCommand socialCommand = new SocialLikeActionApi.SetLikeCommand(
                actorUserId, POST, postId, true, postOwnerId, postId
        );
        when(socialLikeActionApi.setLike(socialCommand))
                .thenReturn(new SocialLikeActionApi.LikeResult(true, 8L));

        LikeResult result = service().setLike(
                new SetLikeCommand(actorUserId, POST, postId, true)
        );

        assertThat(result).isEqualTo(new LikeResult(true, 8L));
        verify(contentEntityQueryApi).resolve(POST, postId);
        verifyNoInteractions(userLookupQueryApi);
        verify(socialLikeActionApi).setLike(socialCommand);
    }

    @Test
    void setLikeShouldConvertCommentOwnerAndParentPostToSocialOwnedTargetView() {
        UUID actorUserId = uuid(1);
        UUID commentId = uuid(21);
        UUID commentOwnerId = uuid(9);
        UUID parentPostId = uuid(11);
        when(contentEntityQueryApi.resolve(COMMENT, commentId))
                .thenReturn(new ResolvedContentRef(commentOwnerId, parentPostId));
        SocialLikeActionApi.SetLikeCommand socialCommand = new SocialLikeActionApi.SetLikeCommand(
                actorUserId, COMMENT, commentId, false, commentOwnerId, parentPostId
        );
        when(socialLikeActionApi.setLike(socialCommand))
                .thenReturn(new SocialLikeActionApi.LikeResult(false, 2L));

        LikeResult result = service().setLike(
                new SetLikeCommand(actorUserId, COMMENT, commentId, false)
        );

        assertThat(result).isEqualTo(new LikeResult(false, 2L));
        verify(contentEntityQueryApi).resolve(COMMENT, commentId);
        verifyNoInteractions(userLookupQueryApi);
        verify(socialLikeActionApi).setLike(socialCommand);
    }

    @Test
    void setLikeShouldNotCallSocialWhenUserTargetDoesNotExist() {
        UUID actorUserId = uuid(1);
        UUID targetUserId = uuid(404);
        when(userLookupQueryApi.getSummaryById(targetUserId)).thenReturn(null);

        assertThatThrownBy(() -> service().setLike(
                new SetLikeCommand(actorUserId, USER, targetUserId, true)
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
                new SetLikeCommand(actorUserId, POST, postId, true)
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
