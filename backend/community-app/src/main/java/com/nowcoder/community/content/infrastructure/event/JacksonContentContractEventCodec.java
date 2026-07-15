package com.nowcoder.community.content.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentContractEventCodec;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ContentTypedEvent;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;

@Component
public class JacksonContentContractEventCodec implements ContentContractEventCodec {

    private final JsonCodec jsonCodec;

    public JacksonContentContractEventCodec(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    @Override
    public ContentTypedEvent decode(ContentContractEvent envelope) {
        Objects.requireNonNull(envelope, "content event envelope must not be null");
        String type = envelope.type();
        JsonNode payload = envelope.payload();
        if (!isKnown(type)) {
            return new ContentTypedEvent.Unknown(
                    envelope.eventId(), envelope.aggregateId(), envelope.aggregateType(), type,
                    envelope.occurredAt(), envelope.version(), payload);
        }
        requireObjectPayload(type, payload);
        return switch (type) {
            case ContentEventTypes.POST_PUBLISHED -> new ContentTypedEvent.PostPublished(
                    envelope.eventId(), envelope.aggregateId(), envelope.aggregateType(),
                    envelope.occurredAt(), envelope.version(), decodePost(type, payload));
            case ContentEventTypes.POST_UPDATED -> new ContentTypedEvent.PostUpdated(
                    envelope.eventId(), envelope.aggregateId(), envelope.aggregateType(),
                    envelope.occurredAt(), envelope.version(), decodePost(type, payload));
            case ContentEventTypes.POST_DELETED -> new ContentTypedEvent.PostDeleted(
                    envelope.eventId(), envelope.aggregateId(), envelope.aggregateType(),
                    envelope.occurredAt(), envelope.version(), decodePost(type, payload));
            case ContentEventTypes.COMMENT_CREATED -> new ContentTypedEvent.CommentCreated(
                    envelope.eventId(), envelope.aggregateId(), envelope.aggregateType(),
                    envelope.occurredAt(), envelope.version(), decodeComment(type, payload));
            case ContentEventTypes.COMMENT_DELETED -> new ContentTypedEvent.CommentDeleted(
                    envelope.eventId(), envelope.aggregateId(), envelope.aggregateType(),
                    envelope.occurredAt(), envelope.version(), decodeComment(type, payload));
            case ContentEventTypes.MODERATION_ACTION_APPLIED -> new ContentTypedEvent.ModerationActionApplied(
                    envelope.eventId(), envelope.aggregateId(), envelope.aggregateType(),
                    envelope.occurredAt(), envelope.version(), decodeModeration(type, payload));
            default -> throw new IllegalStateException("unhandled content event type: " + type);
        };
    }

    @Override
    public ContentContractEvent encode(ContentTypedEvent event) {
        Objects.requireNonNull(event, "content typed event must not be null");
        ContentContractEvent envelope;
        if (event instanceof ContentTypedEvent.PostPublished value) {
            envelope = envelope(value, ContentEventTypes.POST_PUBLISHED, jsonCodec.valueToTree(value.payload()));
        } else if (event instanceof ContentTypedEvent.PostUpdated value) {
            envelope = envelope(value, ContentEventTypes.POST_UPDATED, jsonCodec.valueToTree(value.payload()));
        } else if (event instanceof ContentTypedEvent.PostDeleted value) {
            envelope = envelope(value, ContentEventTypes.POST_DELETED, jsonCodec.valueToTree(value.payload()));
        } else if (event instanceof ContentTypedEvent.CommentCreated value) {
            envelope = envelope(value, ContentEventTypes.COMMENT_CREATED, jsonCodec.valueToTree(value.payload()));
        } else if (event instanceof ContentTypedEvent.CommentDeleted value) {
            envelope = envelope(value, ContentEventTypes.COMMENT_DELETED, jsonCodec.valueToTree(value.payload()));
        } else if (event instanceof ContentTypedEvent.ModerationActionApplied value) {
            envelope = envelope(value, ContentEventTypes.MODERATION_ACTION_APPLIED, jsonCodec.valueToTree(value.payload()));
        } else if (event instanceof ContentTypedEvent.Unknown value) {
            if (isKnown(value.type())) {
                throw new IllegalArgumentException("unknown content event cannot use known type: " + value.type());
            }
            envelope = new ContentContractEvent(
                    value.eventId(), value.aggregateId(), value.aggregateType(), value.type(),
                    value.occurredAt(), value.version(), value.payload());
        } else {
            throw new IllegalArgumentException("unsupported content typed event: " + event.getClass().getName());
        }
        decode(envelope);
        return envelope;
    }

    @Override
    public ContentContractEvent deserialize(String json) {
        JsonNode root = jsonCodec.readTree(json);
        return new ContentContractEvent(
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
    public String serialize(ContentTypedEvent event) {
        return jsonCodec.toJson(encode(event));
    }

    private ContentContractEvent envelope(ContentTypedEvent event, String type, JsonNode payload) {
        return new ContentContractEvent(
                event.eventId(), event.aggregateId(), event.aggregateType(), type,
                event.occurredAt(), event.version(), payload);
    }

    private PostPayload decodePost(String type, JsonNode payload) {
        requireUuid(type, payload, "postId");
        return convert(type, payload, PostPayload.class);
    }

    private CommentPayload decodeComment(String type, JsonNode payload) {
        requireUuid(type, payload, "commentId");
        return convert(type, payload, CommentPayload.class);
    }

    private ModerationPayload decodeModeration(String type, JsonNode payload) {
        requireUuid(type, payload, "toUserId");
        return convert(type, payload, ModerationPayload.class);
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
            throw new IllegalArgumentException("content event outbox payload missing payload: " + type);
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

    private boolean isKnown(String type) {
        return ContentEventTypes.POST_PUBLISHED.equals(type)
                || ContentEventTypes.POST_UPDATED.equals(type)
                || ContentEventTypes.POST_DELETED.equals(type)
                || ContentEventTypes.COMMENT_CREATED.equals(type)
                || ContentEventTypes.COMMENT_DELETED.equals(type)
                || ContentEventTypes.MODERATION_ACTION_APPLIED.equals(type);
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
            throw new IllegalStateException("content event outbox payload invalid " + fieldName, error);
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
            throw new IllegalStateException("content event outbox payload invalid " + fieldName, error);
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
        String message = "invalid content event payload: type=" + type + ", field=" + fieldName;
        return cause == null ? new IllegalArgumentException(message) : new IllegalArgumentException(message, cause);
    }
}
