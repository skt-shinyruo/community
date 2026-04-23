package com.nowcoder.community.user.service;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.wallet.api.action.WalletRewardActionApi;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PointsAwardServiceTest {

    @Test
    void postPublishedShouldAwardAuthorPointsThroughProjectionService() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        PointsAwardService service = new PointsAwardService(new PointsProjectionService(walletRewardActionApi));
        UUID postId = uuid(100);
        UUID userId = uuid(7);

        service.awardPostPublished(postId, userId);

        verify(walletRewardActionApi).applyDelta(
                "wallet-reward:post-published:" + postId,
                userId,
                10,
                ContentEventTypes.POST_PUBLISHED
        );
    }

    @Test
    void commentCreatedShouldAwardCommentAuthorPointsThroughProjectionService() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        PointsAwardService service = new PointsAwardService(new PointsProjectionService(walletRewardActionApi));
        UUID commentId = uuid(200);
        UUID userId = uuid(3);

        CommentPayload payload = new CommentPayload();
        payload.setCommentId(commentId);
        payload.setUserId(userId);

        service.awardCommentCreated(payload);

        verify(walletRewardActionApi).applyDelta(
                "wallet-reward:comment-created:" + commentId,
                userId,
                2,
                ContentEventTypes.COMMENT_CREATED
        );
    }

    @Test
    void likeCreatedAndRemovedShouldAwardEntityOwnerPointsThroughProjectionService() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        PointsAwardService service = new PointsAwardService(new PointsProjectionService(walletRewardActionApi));
        UUID actorUserId = uuid(1);
        UUID entityUserId = uuid(2);

        LikePayload payload = new LikePayload();
        payload.setActorUserId(actorUserId);
        payload.setEntityUserId(entityUserId);

        service.awardLikeCreated("like-created-event", payload);
        service.awardLikeRemoved("like-removed-event", payload);

        verify(walletRewardActionApi).applyDelta(
                "wallet-reward:like-created-event",
                entityUserId,
                1,
                SocialEventTypes.LIKE_CREATED
        );
        verify(walletRewardActionApi).applyDelta(
                "wallet-reward:like-removed-event",
                entityUserId,
                -1,
                SocialEventTypes.LIKE_REMOVED
        );
    }
}
