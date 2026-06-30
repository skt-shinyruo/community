package com.nowcoder.community.im.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ImPolicyBackboneKafkaListener {

    private final ImPolicyChangePublisher imPolicyChangePublisher;
    private final JsonCodec jsonCodec;

    public ImPolicyBackboneKafkaListener(
            ImPolicyChangePublisher imPolicyChangePublisher,
            JsonCodec jsonCodec
    ) {
        this.imPolicyChangePublisher = imPolicyChangePublisher;
        this.jsonCodec = jsonCodec;
    }

    @KafkaListener(
            topics = "${social.events.kafka-topic:social.events}",
            groupId = "${im.policy.kafka.consumer.group-id:im-policy-projection}",
            concurrency = "${im.policy.kafka.consumer.concurrency:3}"
    )
    public void onSocialEvent(SocialContractEvent event) {
        if (event == null || !SocialEventTypes.BLOCK_RELATION_CHANGED.equals(event.type())) {
            return;
        }
        BlockPayload payload = normalizePayload(event.payload());
        if (payload == null
                || payload.getBlockerUserId() == null
                || payload.getBlockedUserId() == null
                || payload.getBlocked() == null) {
            return;
        }
        imPolicyChangePublisher.publishBlockRelationChanged(
                payload.getBlockerUserId(),
                payload.getBlockedUserId(),
                payload.getBlocked(),
                payload.getVersion() == null ? 0L : payload.getVersion()
        );
    }

    private BlockPayload normalizePayload(Object payload) {
        if (payload == null || payload instanceof BlockPayload) {
            return (BlockPayload) payload;
        }
        JsonNode node = jsonCodec.valueToTree(payload);
        return jsonCodec.treeToValue(node, BlockPayload.class);
    }
}
