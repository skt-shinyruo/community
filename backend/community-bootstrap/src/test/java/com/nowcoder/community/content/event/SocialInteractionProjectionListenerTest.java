package com.nowcoder.community.content.event;

import com.nowcoder.community.contracts.domain.EntityTypes;
import com.nowcoder.community.content.score.PostScoreQueue;
import com.nowcoder.community.social.api.event.SocialEventTypes;
import com.nowcoder.community.social.api.event.payload.LikePayload;
import com.nowcoder.community.social.event.SocialLocalEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SocialInteractionProjectionListenerTest {

    @Test
    void postLikeEventsShouldEnqueueScoreRefresh() {
        PostScoreQueue queue = mock(PostScoreQueue.class);
        SocialInteractionProjectionListener listener = new SocialInteractionProjectionListener(queue);

        LikePayload payload = new LikePayload();
        payload.setEntityType(EntityTypes.POST);
        payload.setEntityId(123);
        payload.setPostId(123);

        listener.onSocialEvent(new SocialLocalEvent("like-created-1", SocialEventTypes.LIKE_CREATED, payload));
        listener.onSocialEvent(new SocialLocalEvent("like-removed-1", SocialEventTypes.LIKE_REMOVED, payload));

        verify(queue, times(2)).add(123);
    }
}
