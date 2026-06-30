package com.nowcoder.community.im.infrastructure.event;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ImPolicyBackboneKafkaListenerTest {

    private final JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());

    @Test
    void shouldForwardBlockRelationChangedFromSocialBackbone() {
        ImPolicyChangePublisher publisher = mock(ImPolicyChangePublisher.class);
        ImPolicyBackboneKafkaListener listener = new ImPolicyBackboneKafkaListener(publisher, jsonCodec);

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
    void shouldForwardBlockRelationChangedWhenKafkaPayloadIsMap() {
        ImPolicyChangePublisher publisher = mock(ImPolicyChangePublisher.class);
        ImPolicyBackboneKafkaListener listener = new ImPolicyBackboneKafkaListener(publisher, jsonCodec);

        listener.onSocialEvent(new SocialContractEvent(
                "evt-block-1",
                SocialEventTypes.BLOCK_RELATION_CHANGED,
                Map.of(
                        "blockerUserId", uuid(11).toString(),
                        "blockedUserId", uuid(22).toString(),
                        "blocked", Boolean.TRUE,
                        "version", 123L
                )
        ));

        verify(publisher).publishBlockRelationChanged(
                uuid(11),
                uuid(22),
                true,
                123L
        );
    }

    @Test
    void shouldIgnoreUnsupportedOrInvalidEvents() {
        ImPolicyChangePublisher publisher = mock(ImPolicyChangePublisher.class);
        ImPolicyBackboneKafkaListener listener = new ImPolicyBackboneKafkaListener(publisher, jsonCodec);

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
