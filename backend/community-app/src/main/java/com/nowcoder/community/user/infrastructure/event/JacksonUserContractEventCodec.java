package com.nowcoder.community.user.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserContractEventCodec;
import com.nowcoder.community.user.contracts.event.UserEventTypes;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import com.nowcoder.community.user.contracts.event.UserTypedEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

@Component
public class JacksonUserContractEventCodec implements UserContractEventCodec {

    private final JsonCodec jsonCodec;

    public JacksonUserContractEventCodec(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    @Override
    public UserTypedEvent decode(UserContractEvent envelope) {
        Objects.requireNonNull(envelope, "user event envelope must not be null");
        String type = envelope.type();
        JsonNode payload = envelope.payload();
        if (!isKnown(type)) {
            return new UserTypedEvent.Unknown(envelope.eventId(), type, payload);
        }
        requireObjectPayload(type, payload);
        requireUuid(type, payload, "userId");
        UserPolicyChangedPayload typedPayload = convert(type, payload, UserPolicyChangedPayload.class);
        return new UserTypedEvent.UserPolicyChanged(envelope.eventId(), typedPayload);
    }

    @Override
    public UserContractEvent encode(UserTypedEvent event) {
        Objects.requireNonNull(event, "user typed event must not be null");
        UserContractEvent envelope;
        if (event instanceof UserTypedEvent.UserPolicyChanged value) {
            envelope = new UserContractEvent(
                    value.eventId(), UserEventTypes.USER_POLICY_CHANGED, jsonCodec.valueToTree(value.payload()));
        } else if (event instanceof UserTypedEvent.Unknown value) {
            if (isKnown(value.type())) {
                throw new IllegalArgumentException("unknown user event cannot use known type: " + value.type());
            }
            envelope = new UserContractEvent(value.eventId(), value.type(), value.payload());
        } else {
            throw new IllegalArgumentException("unsupported user typed event: " + event.getClass().getName());
        }
        decode(envelope);
        return envelope;
    }

    @Override
    public UserContractEvent deserialize(String json) {
        JsonNode root = jsonCodec.readTree(json);
        return new UserContractEvent(
                text(root, "eventId"),
                text(root, "type"),
                root == null ? null : root.get("payload")
        );
    }

    @Override
    public String serialize(UserTypedEvent event) {
        return jsonCodec.toJson(encode(event));
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
            throw new IllegalArgumentException("user event outbox payload missing payload: " + type);
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
        return UserEventTypes.USER_POLICY_CHANGED.equals(type);
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    private IllegalArgumentException malformed(String type, String fieldName, Throwable cause) {
        String message = "invalid user event payload: type=" + type + ", field=" + fieldName;
        return cause == null ? new IllegalArgumentException(message) : new IllegalArgumentException(message, cause);
    }
}
