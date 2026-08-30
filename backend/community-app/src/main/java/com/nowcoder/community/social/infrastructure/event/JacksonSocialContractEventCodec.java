package com.nowcoder.community.social.infrastructure.event;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.event.JacksonEventPayloadSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialContractEventCodec;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.social.contracts.event.SocialTypedEvent;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class JacksonSocialContractEventCodec implements SocialContractEventCodec {

    private final JacksonJsonCodec jsonCodec;
    private final JacksonEventPayloadSupport payloadSupport;

    public JacksonSocialContractEventCodec(JacksonJsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
        this.payloadSupport = new JacksonEventPayloadSupport(jsonCodec, "social");
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
        payloadSupport.requireObjectPayload(type, payload);
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
    public String serialize(SocialTypedEvent event) {
        return jsonCodec.toJson(encode(event));
    }

    private SocialContractEvent envelope(SocialTypedEvent event, String type, JsonNode payload) {
        return new SocialContractEvent(
                event.eventId(), event.aggregateId(), event.aggregateType(), type,
                event.occurredAt(), event.version(), payload);
    }

    private LikePayload decodeLike(String type, JsonNode payload) {
        payloadSupport.requireUuid(type, payload, "actorUserId");
        return payloadSupport.convert(type, payload, LikePayload.class);
    }

    private FollowPayload decodeFollow(String type, JsonNode payload) {
        payloadSupport.requireUuid(type, payload, "entityId");
        return payloadSupport.convert(type, payload, FollowPayload.class);
    }

    private BlockPayload decodeBlock(String type, JsonNode payload) {
        payloadSupport.requireUuid(type, payload, "blockerUserId");
        payloadSupport.requireUuid(type, payload, "blockedUserId");
        payloadSupport.requireBoolean(type, payload, "blocked");
        return payloadSupport.convert(type, payload, BlockPayload.class);
    }

    private boolean isKnown(String type) {
        return SocialEventTypes.LIKE_CREATED.equals(type)
                || SocialEventTypes.LIKE_REMOVED.equals(type)
                || SocialEventTypes.FOLLOW_CREATED.equals(type)
                || SocialEventTypes.BLOCK_RELATION_CHANGED.equals(type);
    }

}
