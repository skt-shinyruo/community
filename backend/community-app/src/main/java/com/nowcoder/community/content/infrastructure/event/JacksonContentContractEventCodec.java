package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.event.JacksonEventPayloadSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentContractEventCodec;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ContentTypedEvent;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.content.contracts.event.PostScorePayload;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class JacksonContentContractEventCodec implements ContentContractEventCodec {

    private final JacksonJsonCodec jsonCodec;
    private final JacksonEventPayloadSupport payloadSupport;

    public JacksonContentContractEventCodec(JacksonJsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
        this.payloadSupport = new JacksonEventPayloadSupport(jsonCodec, "content");
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
        payloadSupport.requireObjectPayload(type, payload);
        return switch (type) {
            case ContentEventTypes.POST_PUBLISHED -> new ContentTypedEvent.PostPublished(
                    envelope.eventId(), envelope.aggregateId(), envelope.aggregateType(),
                    envelope.occurredAt(), envelope.version(), decodePost(type, payload));
            case ContentEventTypes.POST_UPDATED -> new ContentTypedEvent.PostUpdated(
                    envelope.eventId(), envelope.aggregateId(), envelope.aggregateType(),
                    envelope.occurredAt(), envelope.version(), decodePost(type, payload));
            case ContentEventTypes.POST_SCORE_UPDATED -> new ContentTypedEvent.PostScoreUpdated(
                    envelope.eventId(), envelope.aggregateId(), envelope.aggregateType(),
                    envelope.occurredAt(), envelope.version(), decodePostScore(type, payload));
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
        } else if (event instanceof ContentTypedEvent.PostScoreUpdated value) {
            envelope = envelope(value, ContentEventTypes.POST_SCORE_UPDATED, jsonCodec.valueToTree(value.payload()));
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
                payloadSupport.text(root, "eventId"),
                payloadSupport.uuid(root, "aggregateId"),
                payloadSupport.text(root, "aggregateType"),
                payloadSupport.text(root, "type"),
                payloadSupport.instant(root, "occurredAt"),
                payloadSupport.number(root, "version"),
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
        payloadSupport.requireUuid(type, payload, "postId");
        return payloadSupport.convert(type, payload, PostPayload.class);
    }

    private PostScorePayload decodePostScore(String type, JsonNode payload) {
        payloadSupport.requireUuid(type, payload, "postId");
        return payloadSupport.convert(type, payload, PostScorePayload.class);
    }

    private CommentPayload decodeComment(String type, JsonNode payload) {
        payloadSupport.requireUuid(type, payload, "commentId");
        return payloadSupport.convert(type, payload, CommentPayload.class);
    }

    private ModerationPayload decodeModeration(String type, JsonNode payload) {
        payloadSupport.requireUuid(type, payload, "toUserId");
        return payloadSupport.convert(type, payload, ModerationPayload.class);
    }

    private boolean isKnown(String type) {
        return ContentEventTypes.POST_PUBLISHED.equals(type)
                || ContentEventTypes.POST_UPDATED.equals(type)
                || ContentEventTypes.POST_SCORE_UPDATED.equals(type)
                || ContentEventTypes.POST_DELETED.equals(type)
                || ContentEventTypes.COMMENT_CREATED.equals(type)
                || ContentEventTypes.COMMENT_DELETED.equals(type)
                || ContentEventTypes.MODERATION_ACTION_APPLIED.equals(type);
    }

}
