package com.nowcoder.community.user.event;

import com.nowcoder.community.content.event.ContentEventTypes;
import com.nowcoder.community.content.event.payload.PostPayload;
import com.nowcoder.community.content.event.ContentLocalEvent;
import com.nowcoder.community.growth.service.UnifiedGrantService;
import com.nowcoder.community.social.event.SocialEventTypes;
import com.nowcoder.community.social.event.payload.LikePayload;
import com.nowcoder.community.social.event.SocialLocalEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PointsProjectionListenerTest {

    @Test
    void postPublishedShouldAwardAuthorPoints() {
        UnifiedGrantService unifiedGrantService = mock(UnifiedGrantService.class);
        PointsProjectionListener listener = new PointsProjectionListener(unifiedGrantService);

        PostPayload payload = new PostPayload();
        payload.setUserId(7);

        listener.onContentEvent(new ContentLocalEvent("post-evt-1", ContentEventTypes.POST_PUBLISHED, payload));

        verify(unifiedGrantService).applyGrant(
                7,
                "post-evt-1:points",
                ContentEventTypes.POST_PUBLISHED,
                "post-evt-1",
                ContentEventTypes.POST_PUBLISHED,
                10,
                0,
                "points",
                "content-event"
        );
    }

    @Test
    void likeRemovedShouldSubtractPointsFromEntityOwner() {
        UnifiedGrantService unifiedGrantService = mock(UnifiedGrantService.class);
        PointsProjectionListener listener = new PointsProjectionListener(unifiedGrantService);

        LikePayload payload = new LikePayload();
        payload.setActorUserId(2);
        payload.setEntityUserId(9);

        listener.onSocialEvent(new SocialLocalEvent("like-evt-2", SocialEventTypes.LIKE_REMOVED, payload));

        verify(unifiedGrantService).applyGrant(
                9,
                "like-evt-2:points",
                SocialEventTypes.LIKE_REMOVED,
                "like-evt-2",
                SocialEventTypes.LIKE_REMOVED,
                -1,
                0,
                "points",
                "social-event"
        );
    }
}
