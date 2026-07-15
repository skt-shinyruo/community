package com.nowcoder.community.social.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialContractEventCodec;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.social.contracts.event.SocialTypedEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;

@Component
public class JacksonSocialContractEventCodec implements SocialContractEventCodec {

    private final JsonCodec jsonCodec;

    public JacksonSocialContractEventCodec(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    @Override
    public SocialTypedEvent decode(SocialContractEvent envelope) {
        Objects.requireNonNull(envelope, "social event envelope must not be null");
        String type = envelope.type();
        JsonNode payload = envelope.payload();
        if (!isKnown(type)) {
            return new SocialTypedEvent.Unknown(
                    envelope.eventId(), envelope.aggregateId(), envelope.aggregateType(), type,
                    envelope.occurredAt(), envelope.version(), payload);
        }
        requireObjectPayload(type, payload);
        return switch (type) {
            case SocialEventTypes.LIKE_CREATED -> new SocialTypedEvent.LikeCreated(
                    envelope.eventId(), envelope.aggregateId(), envelope.aggregateType(),
                    envelope.occurredAt(), envelope.version(), decodeLike(type, payload));
            case SocialEventTypes.LIKE_REMOVED -> new SocialTypedEvent.LikeRemoved(
                    envelope.eventId(), envelope.aggregateId(), envelope.aggregateType(),
                    envelope.occurredAt(), envelope.version(), decodeLike(type, payload));
            case SocialEventTypes.FOLLOW_CREATED -> new SocialTypedEvent.FollowCreated(
                    envelope.eventId(), envelope.aggregateId(), envelope.aggregateType(),
                    envelope.occurredAt(), envelope.version(), decodeFollow(type, payload));
            case SocialEventTypes.BLOCK_RELATION_CHANGED -> new SocialTypedEvent.BlockRelationChanged(
                    envelope.eventId(), envelope.aggregateId(), envelope.aggregateType(),
                    envelope.occurredAt(), envelope.version(), decodeBlock(type, payload));
            default -> throw new IllegalStateException("unhandled social event type: " + type);
        };
    }

    @Override
    public SocialContractEvent encode(SocialTypedEvent event) {
        Objects.requireNonNull(event, "social typed event must not be null");
        SocialContractEvent envelope;
        if (event instanceof SocialTypedEvent.LikeCreated value) {
            envelope = envelope(value, SocialEventTypes.LIKE_CREATED, jsonCodec.valueToTree(value.payload()));
        } else if (event instanceof SocialTypedEvent.LikeRemoved value) {
            envelope = envelope(value, SocialEventTypes.LIKE_REMOVED, jsonCodec.valueToTree(value.payload()));
        } else if (event instanceof SocialTypedEvent.FollowCreated value) {
            envelope = envelope(value, SocialEventTypes.FOLLOW_CREATED, jsonCodec.valueToTree(value.payload()));
        } else if (event instanceof SocialTypedEvent.BlockRelationChanged value) {
            envelope = envelope(value, SocialEventTypes.BLOCK_RELATION_CHANGED, jsonCodec.valueToTree(value.payload()));
        } else if (event instanceof SocialTypedEvent.Unknown value) {
            if (isKnown(value.type())) {
                throw new IllegalArgumentException("unknown social event cannot use known type: " + value.type());
            }
            envelope = new SocialContractEvent(
                    value.eventId(), value.aggregateId(), value.aggregateType(), value.type(),
                    value.occurredAt(), value.version(), value.payload());
        } else {
            throw new IllegalArgumentException("unsupported social typed event: " + event.getClass().getName());
        }
        decode(envelope);
        return envelope;
    }

    @Override
    public SocialContractEvent deserialize(String json) {
        JsonNode root = jsonCodec.readTree(json);
        return new SocialContractEvent(
                text(root, "eventId"),
                uuid(root, "aggregateId"),
                text(root, "aggregateType"),
                text(root, "type"),
                instant(root, "occurredAt"),
                number(root, "version"),
                root == null ? null : root.get("payload")
        );
    }

    @Override
    public String serialize(SocialTypedEvent event) {
        return jsonCodec.toJson(encode(event));
    }

    private SocialContractEvent envelope(SocialTypedEvent event, String type, JsonNode payload) {
        return new SocialContractEvent(
                event.eventId(), event.aggregateId(), event.aggregateType(), type,
                event.occurredAt(), event.version(), payload);
    }

    private LikePayload decodeLike(String type, JsonNode payload) {
        requireUuid(type, payload, "actorUserId");
        return convert(type, payload, LikePayload.class);
    }

    private FollowPayload decodeFollow(String type, JsonNode payload) {
        requireUuid(type, payload, "entityId");
        return convert(type, payload, FollowPayload.class);
    }

    private BlockPayload decodeBlock(String type, JsonNode payload) {
        requireUuid(type, payload, "blockerUserId");
        requireUuid(type, payload, "blockedUserId");
        requireBoolean(type, payload, "blocked");
        return convert(type, payload, BlockPayload.class);
    }

    private <T> T convert(String type, JsonNode payload, Class<T> payloadType) {
        try {
            return jsonCodec.treeToValue(payload, payloadType);
        } catch (RuntimeException error) {
            throw malformed(type, "payload", error);
        }
    }

    private void requireObjectPayload(String type, JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("social event outbox payload missing payload: " + type);
        }
        if (!payload.isObject()) {
            throw malformed(type, "payload", null);
        }
    }

    private void requireUuid(String type, JsonNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        if (value == null || !value.isTextual() || !StringUtils.hasText(value.textValue())) {
            throw malformed(type, fieldName, null);
        }
        try {
            UUID.fromString(value.textValue());
        } catch (IllegalArgumentException error) {
            throw malformed(type, fieldName, error);
        }
    }

    private void requireBoolean(String type, JsonNode payload, String fieldName) {
        JsonNode value = payload.get(fieldName);
        if (value == null || !value.isBoolean()) {
            throw malformed(type, fieldName, null);
        }
    }

    private boolean isKnown(String type) {
        return SocialEventTypes.LIKE_CREATED.equals(type)
                || SocialEventTypes.LIKE_REMOVED.equals(type)
                || SocialEventTypes.FOLLOW_CREATED.equals(type)
                || SocialEventTypes.BLOCK_RELATION_CHANGED.equals(type);
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null) {
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
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("social event outbox payload invalid " + fieldName, error);
        }
    }

    private Instant instant(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException error) {
            throw new IllegalStateException("social event outbox payload invalid " + fieldName, error);
        }
    }

    private long number(JsonNode node, String fieldName) {
        if (node == null) {
            return 0L;
        }
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? 0L : value.asLong(0L);
    }

    private IllegalArgumentException malformed(String type, String fieldName, Throwable cause) {
        String message = "invalid social event payload: type=" + type + ", field=" + fieldName;
        return cause == null ? new IllegalArgumentException(message) : new IllegalArgumentException(message, cause);
    }
}
