package com.nowcoder.community.user.infrastructure.event;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.event.JacksonEventPayloadSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserContractEventCodec;
import com.nowcoder.community.user.contracts.event.UserEventTypes;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import com.nowcoder.community.user.contracts.event.UserTypedEvent;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class JacksonUserContractEventCodec implements UserContractEventCodec {

    private final JacksonJsonCodec jsonCodec;
    private final JacksonEventPayloadSupport payloadSupport;

    public JacksonUserContractEventCodec(JacksonJsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
        this.payloadSupport = new JacksonEventPayloadSupport(jsonCodec, "user");
    }

    @Override
    public UserTypedEvent decode(UserContractEvent envelope) {
        Objects.requireNonNull(envelope, "user event envelope must not be null");
        String type = envelope.type();
        JsonNode payload = envelope.payload();
        if (!isKnown(type)) {
            return new UserTypedEvent.Unknown(envelope.eventId(), type, payload);
        }
        payloadSupport.requireObjectPayload(type, payload);
        payloadSupport.requireUuid(type, payload, "userId");
        UserPolicyChangedPayload typedPayload = payloadSupport.convert(type, payload, UserPolicyChangedPayload.class);
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
                payloadSupport.text(root, "eventId"),
                payloadSupport.text(root, "type"),
                root == null ? null : root.get("payload")
        );
    }

    @Override
    public String serialize(UserTypedEvent event) {
        return jsonCodec.toJson(encode(event));
    }

    private boolean isKnown(String type) {
        return UserEventTypes.USER_POLICY_CHANGED.equals(type);
    }

}
