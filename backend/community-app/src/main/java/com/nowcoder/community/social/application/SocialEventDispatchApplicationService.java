package com.nowcoder.community.social.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.social.application.command.DispatchSocialEventCommand;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnExpression("'${social.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'")
public class SocialEventDispatchApplicationService {

    private final JsonCodec jsonCodec;
    private final SocialIntegrationEventDispatcher dispatcher;

    public SocialEventDispatchApplicationService(
            JsonCodec jsonCodec,
            SocialIntegrationEventDispatcher dispatcher
    ) {
        this.jsonCodec = jsonCodec;
        this.dispatcher = dispatcher;
    }

    public void dispatch(DispatchSocialEventCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!StringUtils.hasText(command.payloadJson())) {
            throw new IllegalStateException("social event outbox payload is blank");
        }

        SocialContractEvent contractEvent = parseContractEvent(command.payloadJson());
        if (!StringUtils.hasText(contractEvent.eventId())) {
            throw new IllegalStateException("social event outbox payload missing eventId");
        }
        if (!StringUtils.hasText(contractEvent.type())) {
            throw new IllegalStateException("social event outbox payload missing type");
        }
        if (contractEvent.aggregateId() == null) {
            throw new IllegalStateException("social event outbox payload missing aggregateId");
        }
        if (!StringUtils.hasText(contractEvent.aggregateType())) {
            throw new IllegalStateException("social event outbox payload missing aggregateType");
        }
        if (contractEvent.occurredAt() == null) {
            throw new IllegalStateException("social event outbox payload missing occurredAt");
        }
        if (contractEvent.version() <= 0L) {
            throw new IllegalStateException("social event outbox payload missing version");
        }
        dispatcher.dispatch(command.eventKey(), contractEvent);
    }

    private SocialContractEvent parseContractEvent(String payloadJson) {
        try {
            JsonNode root = jsonCodec.readTree(payloadJson);
            String eventId = text(root, "eventId");
            UUID aggregateId = uuid(root, "aggregateId");
            String aggregateType = text(root, "aggregateType");
            String type = text(root, "type");
            Instant occurredAt = instant(root, "occurredAt");
            long version = number(root, "version");
            Object payload = typedPayload(type, root.get("payload"));
            return new SocialContractEvent(eventId, aggregateId, aggregateType, type, occurredAt, version, payload);
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

    private UUID uuid(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("social event outbox payload invalid " + fieldName, e);
        }
    }

    private Instant instant(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("social event outbox payload invalid " + fieldName, e);
        }
    }

    private long number(JsonNode node, String fieldName) {
        if (node == null || fieldName == null) {
            return 0L;
        }
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? 0L : value.asLong(0L);
    }
}
