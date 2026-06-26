package com.nowcoder.community.content.event;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.application.SocialInteractionProjectionApplicationService;
import com.nowcoder.community.content.infrastructure.event.SocialInteractionBackboneKafkaListener;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SocialInteractionBackboneKafkaListenerTest {

    @Test
    void shouldProjectPostLikeEventsFromSocialBackbone() {
        SocialInteractionProjectionApplicationService applicationService = mock(SocialInteractionProjectionApplicationService.class);
        SocialInteractionBackboneKafkaListener listener = new SocialInteractionBackboneKafkaListener(applicationService);

        LikePayload payload = new LikePayload();
        payload.setActorUserId(uuid(1));
        payload.setEntityType(EntityTypes.POST);
        payload.setEntityId(uuid(99));
        payload.setPostId(payload.getEntityId());

        listener.onSocialEvent(new SocialContractEvent("evt-like-1", SocialEventTypes.LIKE_CREATED, payload));

        verify(applicationService).projectSocialEvent(any(SocialContractEvent.class));
    }
}
