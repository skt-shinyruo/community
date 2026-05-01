package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.application.PostScoreQueue;
import com.nowcoder.community.content.application.SocialInteractionProjectionApplicationService;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SocialInteractionProjectionListenerTest {

    @Test
    void postLikeEventsShouldEnqueueScoreRefresh() {
        PostScoreQueue queue = mock(PostScoreQueue.class);
        SocialInteractionProjectionListener listener =
                new SocialInteractionProjectionListener(new SocialInteractionProjectionApplicationService(queue));

        UUID postId = uuid(123);
        LikePayload payload = new LikePayload();
        payload.setEntityType(EntityTypes.POST);
        payload.setEntityId(postId);
        payload.setPostId(postId);

        listener.onSocialEvent(new SocialContractEvent("like-created-1", SocialEventTypes.LIKE_CREATED, payload));
        listener.onSocialEvent(new SocialContractEvent("like-removed-1", SocialEventTypes.LIKE_REMOVED, payload));

        verify(queue, times(2)).add(postId);
    }

    @Test
    void postLikeProjectionFailureShouldRemainBestEffort() {
        PostScoreQueue queue = mock(PostScoreQueue.class);
        SocialInteractionProjectionListener listener =
                new SocialInteractionProjectionListener(new SocialInteractionProjectionApplicationService(queue));

        UUID postId = uuid(123);
        LikePayload payload = new LikePayload();
        payload.setEntityType(EntityTypes.POST);
        payload.setEntityId(postId);
        payload.setPostId(postId);
        doThrow(new RuntimeException("redis unavailable")).when(queue).add(postId);

        assertThatCode(() -> listener.onSocialEvent(new SocialContractEvent(
                "like-created-1",
                SocialEventTypes.LIKE_CREATED,
                payload
        ))).doesNotThrowAnyException();

        verify(queue).add(postId);
    }
}
