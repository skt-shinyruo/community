package com.nowcoder.community.content.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.content.application.command.DispatchContentEventCommand;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;

@Service
public class ContentEventDispatchApplicationService {

    private final JsonCodec jsonCodec;
    private final ContentIntegrationEventDispatcher dispatcher;

    public ContentEventDispatchApplicationService(
            JsonCodec jsonCodec,
            ContentIntegrationEventDispatcher dispatcher
    ) {
        this.jsonCodec = jsonCodec;
        this.dispatcher = dispatcher;
    }

    public void dispatch(DispatchContentEventCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!StringUtils.hasText(command.payloadJson())) {
            throw new IllegalStateException("content event outbox payload is blank");
        }

        ContentContractEvent contractEvent = parseContractEvent(command.payloadJson());
        if (!StringUtils.hasText(contractEvent.eventId())) {
            throw new IllegalStateException("content event outbox payload missing eventId");
        }
        if (!StringUtils.hasText(contractEvent.type())) {
            throw new IllegalStateException("content event outbox payload missing type");
        }
        if (contractEvent.aggregateId() == null) {
            throw new IllegalStateException("content event outbox payload missing aggregateId");
        }
        if (!StringUtils.hasText(contractEvent.aggregateType())) {
            throw new IllegalStateException("content event outbox payload missing aggregateType");
        }
        if (contractEvent.occurredAt() == null) {
            throw new IllegalStateException("content event outbox payload missing occurredAt");
        }
        if (contractEvent.version() <= 0L) {
            throw new IllegalStateException("content event outbox payload missing version");
        }
        dispatcher.dispatch(command.eventKey(), contractEvent);
    }

    private ContentContractEvent parseContractEvent(String payloadJson) {
        try {
            JsonNode root = jsonCodec.readTree(payloadJson);
            String eventId = text(root, "eventId");
            UUID aggregateId = uuid(root, "aggregateId");
            String aggregateType = text(root, "aggregateType");
            String type = text(root, "type");
            Instant occurredAt = instant(root, "occurredAt");
            long version = number(root, "version");
            Object payload = typedPayload(type, root.get("payload"));
            return new ContentContractEvent(eventId, aggregateId, aggregateType, type, occurredAt, version, payload);
        } catch (JsonCodecException e) {
            throw new IllegalStateException("content event outbox payload deserialization failed", e);
        }
    }

    private Object typedPayload(String type, JsonNode payload) {
        if (isKnownPayloadType(type) && (payload == null || payload.isNull())) {
            throw new IllegalStateException("content event outbox payload missing payload");
        }
        if (payload == null || payload.isNull()) {
            return null;
        }
        if (ContentEventTypes.POST_PUBLISHED.equals(type)
                || ContentEventTypes.POST_UPDATED.equals(type)
                || ContentEventTypes.POST_DELETED.equals(type)) {
            return jsonCodec.treeToValue(payload, PostPayload.class);
        }
        if (ContentEventTypes.COMMENT_CREATED.equals(type) || ContentEventTypes.COMMENT_DELETED.equals(type)) {
            return jsonCodec.treeToValue(payload, CommentPayload.class);
        }
        if (ContentEventTypes.MODERATION_ACTION_APPLIED.equals(type)) {
            return jsonCodec.treeToValue(payload, ModerationPayload.class);
        }
        return jsonCodec.treeToValue(payload, Object.class);
    }

    private boolean isKnownPayloadType(String type) {
        return ContentEventTypes.POST_PUBLISHED.equals(type)
                || ContentEventTypes.POST_UPDATED.equals(type)
                || ContentEventTypes.POST_DELETED.equals(type)
                || ContentEventTypes.COMMENT_CREATED.equals(type)
                || ContentEventTypes.COMMENT_DELETED.equals(type)
                || ContentEventTypes.MODERATION_ACTION_APPLIED.equals(type);
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
            throw new IllegalStateException("content event outbox payload invalid " + fieldName, e);
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
            throw new IllegalStateException("content event outbox payload invalid " + fieldName, e);
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
