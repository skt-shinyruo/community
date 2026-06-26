package com.nowcoder.community.im.infrastructure.event;

import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ImPolicyBackboneKafkaListenerTest {

    @Test
    void shouldForwardBlockRelationChangedFromSocialBackbone() {
        ImPolicyChangePublisher publisher = mock(ImPolicyChangePublisher.class);
        ImPolicyBackboneKafkaListener listener = new ImPolicyBackboneKafkaListener(publisher);

        BlockPayload payload = new BlockPayload();
        payload.setBlockerUserId(uuid(11));
        payload.setBlockedUserId(uuid(22));
        payload.setBlocked(Boolean.TRUE);
        payload.setVersion(123L);

        listener.onSocialEvent(new SocialContractEvent(
                "evt-block-1",
                SocialEventTypes.BLOCK_RELATION_CHANGED,
                payload
        ));

        verify(publisher).publishBlockRelationChanged(
                payload.getBlockerUserId(),
                payload.getBlockedUserId(),
                true,
                123L
        );
    }

    @Test
    void shouldIgnoreUnsupportedOrInvalidEvents() {
        ImPolicyChangePublisher publisher = mock(ImPolicyChangePublisher.class);
        ImPolicyBackboneKafkaListener listener = new ImPolicyBackboneKafkaListener(publisher);

        listener.onSocialEvent(null);
        listener.onSocialEvent(new SocialContractEvent("evt-follow-1", SocialEventTypes.FOLLOW_CREATED, new Object()));

        BlockPayload missingUsers = new BlockPayload();
        missingUsers.setBlocked(Boolean.TRUE);
        listener.onSocialEvent(new SocialContractEvent(
                "evt-block-invalid",
                SocialEventTypes.BLOCK_RELATION_CHANGED,
                missingUsers
        ));

        verifyNoInteractions(publisher);
    }
}
