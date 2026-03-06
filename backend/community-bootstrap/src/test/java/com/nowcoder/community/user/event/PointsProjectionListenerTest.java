package com.nowcoder.community.user.event;

import com.nowcoder.community.content.api.event.ContentEventTypes;
import com.nowcoder.community.content.api.event.payload.PostPayload;
import com.nowcoder.community.content.event.ContentLocalEvent;
import com.nowcoder.community.social.api.event.SocialEventTypes;
import com.nowcoder.community.social.api.event.payload.LikePayload;
import com.nowcoder.community.social.event.SocialLocalEvent;
import com.nowcoder.community.user.service.PointsService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PointsProjectionListenerTest {

    @Test
    void postPublishedShouldAwardAuthorPoints() {
        PointsService pointsService = mock(PointsService.class);
        PointsProjectionListener listener = new PointsProjectionListener(pointsService);

        PostPayload payload = new PostPayload();
        payload.setUserId(7);

        listener.onContentEvent(new ContentLocalEvent("post-evt-1", ContentEventTypes.POST_PUBLISHED, payload));

        verify(pointsService).applyPoints(7, "post-evt-1", ContentEventTypes.POST_PUBLISHED, 10);
    }

    @Test
    void likeRemovedShouldSubtractPointsFromEntityOwner() {
        PointsService pointsService = mock(PointsService.class);
        PointsProjectionListener listener = new PointsProjectionListener(pointsService);

        LikePayload payload = new LikePayload();
        payload.setActorUserId(2);
        payload.setEntityUserId(9);

        listener.onSocialEvent(new SocialLocalEvent("like-evt-2", SocialEventTypes.LIKE_REMOVED, payload));

        verify(pointsService).applyPoints(9, "like-evt-2", SocialEventTypes.LIKE_REMOVED, -1);
    }
}
