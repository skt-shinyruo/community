package com.nowcoder.community.user.event;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.growth.api.action.GrowthGrantActionApi;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PointsProjectionListenerTest {

    @Test
    void postPublishedShouldAwardAuthorPoints() {
        GrowthGrantActionApi growthGrantActionApi = mock(GrowthGrantActionApi.class);
        PointsProjectionListener listener = new PointsProjectionListener(growthGrantActionApi);

        PostPayload payload = new PostPayload();
        payload.setUserId(7);

        listener.onContentEvent(new ContentContractEvent("post-evt-1", ContentEventTypes.POST_PUBLISHED, payload));

        verify(growthGrantActionApi).applyPointsProjection(
                7,
                "post-evt-1",
                ContentEventTypes.POST_PUBLISHED,
                10
        );
    }

    @Test
    void likeRemovedShouldSubtractPointsFromEntityOwner() {
        GrowthGrantActionApi growthGrantActionApi = mock(GrowthGrantActionApi.class);
        PointsProjectionListener listener = new PointsProjectionListener(growthGrantActionApi);

        LikePayload payload = new LikePayload();
        payload.setActorUserId(2);
        payload.setEntityUserId(9);

        listener.onSocialEvent(new SocialContractEvent("like-evt-2", SocialEventTypes.LIKE_REMOVED, payload));

        verify(growthGrantActionApi).applyPointsProjection(
                9,
                "like-evt-2",
                SocialEventTypes.LIKE_REMOVED,
                -1
        );
    }
}
