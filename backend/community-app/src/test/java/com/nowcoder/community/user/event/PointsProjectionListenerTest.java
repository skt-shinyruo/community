package com.nowcoder.community.user.event;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.wallet.service.WalletRewardService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PointsProjectionListenerTest {

    @Test
    void postPublishedShouldAwardAuthorPoints() {
        WalletRewardService walletRewardService = mock(WalletRewardService.class);
        PointsProjectionListener listener = new PointsProjectionListener(walletRewardService);
        UUID userId = uuid(7);

        PostPayload payload = new PostPayload();
        payload.setUserId(userId);

        listener.onContentEvent(new ContentContractEvent("post-evt-1", ContentEventTypes.POST_PUBLISHED, payload));

        verify(walletRewardService).applyDelta(
                "wallet-reward:post-evt-1",
                userId,
                10,
                ContentEventTypes.POST_PUBLISHED
        );
    }

    @Test
    void likeRemovedShouldSubtractPointsFromEntityOwner() {
        WalletRewardService walletRewardService = mock(WalletRewardService.class);
        PointsProjectionListener listener = new PointsProjectionListener(walletRewardService);
        UUID actorUserId = uuid(2);
        UUID entityUserId = uuid(9);

        LikePayload payload = new LikePayload();
        payload.setActorUserId(actorUserId);
        payload.setEntityUserId(entityUserId);

        listener.onSocialEvent(new SocialContractEvent("like-evt-2", SocialEventTypes.LIKE_REMOVED, payload));

        verify(walletRewardService).applyDelta(
                "wallet-reward:like-evt-2",
                entityUserId,
                -1,
                SocialEventTypes.LIKE_REMOVED
        );
    }
}
