package com.nowcoder.community.user.event;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.wallet.service.WalletRewardService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PointsProjectionListenerTest {

    @Test
    void postPublishedShouldAwardAuthorPoints() {
        WalletRewardService walletRewardService = mock(WalletRewardService.class);
        PointsProjectionListener listener = new PointsProjectionListener(walletRewardService);

        PostPayload payload = new PostPayload();
        payload.setUserId(7);

        listener.onContentEvent(new ContentContractEvent("post-evt-1", ContentEventTypes.POST_PUBLISHED, payload));

        verify(walletRewardService).applyDelta(
                "wallet-reward:post-evt-1",
                7,
                10,
                ContentEventTypes.POST_PUBLISHED
        );
    }

    @Test
    void likeRemovedShouldSubtractPointsFromEntityOwner() {
        WalletRewardService walletRewardService = mock(WalletRewardService.class);
        PointsProjectionListener listener = new PointsProjectionListener(walletRewardService);

        LikePayload payload = new LikePayload();
        payload.setActorUserId(2);
        payload.setEntityUserId(9);

        listener.onSocialEvent(new SocialContractEvent("like-evt-2", SocialEventTypes.LIKE_REMOVED, payload));

        verify(walletRewardService).applyDelta(
                "wallet-reward:like-evt-2",
                9,
                -1,
                SocialEventTypes.LIKE_REMOVED
        );
    }
}
