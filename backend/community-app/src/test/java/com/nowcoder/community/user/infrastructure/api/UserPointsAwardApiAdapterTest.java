package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.user.api.model.UserCommentPointsAwardRequest;
import com.nowcoder.community.user.api.model.UserLikePointsAwardRequest;
import com.nowcoder.community.user.application.UserPointsApplicationService;
import com.nowcoder.community.wallet.api.action.WalletRewardActionApi;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class UserPointsAwardApiAdapterTest {

    @Test
    void postPublishedShouldAwardAuthorPointsThroughProjectionService() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        UserPointsAwardApiAdapter service = new UserPointsAwardApiAdapter(new UserPointsApplicationService(walletRewardActionApi));
        UUID postId = uuid(100);
        UUID userId = uuid(7);

        service.awardPostPublished(postId, userId);

        verify(walletRewardActionApi).applyDelta(
                "wallet-reward:post-published:" + postId,
                userId,
                10,
                "PostPublished"
        );
    }

    @Test
    void commentCreatedShouldAwardCommentAuthorPointsThroughProjectionService() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        UserPointsAwardApiAdapter service = new UserPointsAwardApiAdapter(new UserPointsApplicationService(walletRewardActionApi));
        UUID commentId = uuid(200);
        UUID userId = uuid(3);

        service.awardCommentCreated(new UserCommentPointsAwardRequest(commentId, userId));

        verify(walletRewardActionApi).applyDelta(
                "wallet-reward:comment-created:" + commentId,
                userId,
                2,
                "CommentCreated"
        );
    }

    @Test
    void likeCreatedAndRemovedShouldAwardEntityOwnerPointsThroughProjectionService() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        UserPointsAwardApiAdapter service = new UserPointsAwardApiAdapter(new UserPointsApplicationService(walletRewardActionApi));
        UUID actorUserId = uuid(1);
        UUID entityUserId = uuid(2);

        service.awardLikeCreated(new UserLikePointsAwardRequest("like-created-event", actorUserId, entityUserId));
        service.awardLikeRemoved(new UserLikePointsAwardRequest("like-removed-event", actorUserId, entityUserId));

        verify(walletRewardActionApi).applyDelta(
                "wallet-reward:like-created-event",
                entityUserId,
                1,
                "LikeCreated"
        );
        verify(walletRewardActionApi).applyDelta(
                "wallet-reward:like-removed-event",
                entityUserId,
                -1,
                "LikeRemoved"
        );
    }

    @Test
    void selfLikeShouldRemainNoOp() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        UserPointsAwardApiAdapter service = new UserPointsAwardApiAdapter(new UserPointsApplicationService(walletRewardActionApi));
        UUID userId = uuid(9);

        service.awardLikeCreated(new UserLikePointsAwardRequest("like-created-event", userId, userId));

        verifyNoInteractions(walletRewardActionApi);
    }
}
