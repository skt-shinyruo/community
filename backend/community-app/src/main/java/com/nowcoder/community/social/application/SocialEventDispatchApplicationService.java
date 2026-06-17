package com.nowcoder.community.social.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnExpression("'${social.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'")
public class SocialEventDispatchApplicationService {

    private final JsonCodec jsonCodec;
    private final SocialEventKafkaDispatchPort dispatchPort;
    private final String kafkaTopic;

    public SocialEventDispatchApplicationService(
            JsonCodec jsonCodec,
            SocialEventKafkaDispatchPort dispatchPort,
            @Value("${social.events.kafka-topic:social.events}") String kafkaTopic
    ) {
        this.jsonCodec = jsonCodec;
        this.dispatchPort = dispatchPort;
        this.kafkaTopic = kafkaTopic;
    }

    public void dispatch(String key, String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            throw new IllegalStateException("social event outbox payload is blank");
        }

        SocialContractEvent contractEvent = parseContractEvent(payloadJson);
        if (!StringUtils.hasText(contractEvent.eventId())) {
            throw new IllegalStateException("social event outbox payload missing eventId");
        }
        if (!StringUtils.hasText(contractEvent.type())) {
            throw new IllegalStateException("social event outbox payload missing type");
        }
        dispatchPort.send(kafkaTopic, key, contractEvent);
    }

    private SocialContractEvent parseContractEvent(String payloadJson) {
        try {
            JsonNode root = jsonCodec.readTree(payloadJson);
            String eventId = text(root, "eventId");
            String type = text(root, "type");
            Object payload = typedPayload(type, root.get("payload"));
            return new SocialContractEvent(eventId, type, payload);
        } catch (JsonCodecException e) {
            throw new IllegalStateException("social event outbox payload deserialization failed", e);
        }
    }

    private Object typedPayload(String type, JsonNode payload) {
        if (isKnownPayloadType(type) && (payload == null || payload.isNull())) {
            throw new IllegalStateException("social event outbox payload missing payload");
        }
        if (payload == null || payload.isNull()) {
            return null;
        }
        if (SocialEventTypes.LIKE_CREATED.equals(type) || SocialEventTypes.LIKE_REMOVED.equals(type)) {
            return jsonCodec.treeToValue(payload, LikePayload.class);
        }
        if (SocialEventTypes.FOLLOW_CREATED.equals(type)) {
            return jsonCodec.treeToValue(payload, FollowPayload.class);
        }
        if (SocialEventTypes.BLOCK_RELATION_CHANGED.equals(type)) {
            return jsonCodec.treeToValue(payload, BlockPayload.class);
        }
        return jsonCodec.treeToValue(payload, Object.class);
    }

    private boolean isKnownPayloadType(String type) {
        return SocialEventTypes.LIKE_CREATED.equals(type)
                || SocialEventTypes.LIKE_REMOVED.equals(type)
                || SocialEventTypes.FOLLOW_CREATED.equals(type)
                || SocialEventTypes.BLOCK_RELATION_CHANGED.equals(type);
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || fieldName == null) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText(null);
    }
}
